package com.jozufozu.flywheel.backend.engine.indirect;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30.glUniform1ui;
import static org.lwjgl.opengl.GL40.glDrawElementsIndirect;
import static org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.glDispatchCompute;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.jozufozu.flywheel.api.context.Context;
import com.jozufozu.flywheel.api.event.RenderStage;
import com.jozufozu.flywheel.api.instance.Instance;
import com.jozufozu.flywheel.api.instance.InstanceType;
import com.jozufozu.flywheel.api.material.Material;
import com.jozufozu.flywheel.api.model.Model;
import com.jozufozu.flywheel.backend.compile.IndirectPrograms;
import com.jozufozu.flywheel.backend.engine.MaterialRenderState;
import com.jozufozu.flywheel.backend.engine.MeshPool;
import com.jozufozu.flywheel.backend.engine.textures.TextureBinder;
import com.jozufozu.flywheel.backend.engine.textures.TextureSourceImpl;
import com.jozufozu.flywheel.backend.engine.uniform.Uniforms;
import com.jozufozu.flywheel.backend.gl.Driver;
import com.jozufozu.flywheel.backend.gl.GlCompat;
import com.jozufozu.flywheel.backend.gl.shader.GlProgram;
import com.jozufozu.flywheel.lib.context.SimpleContextShader;

public class IndirectCullingGroup<I extends Instance> {
	private static final Comparator<IndirectDraw> DRAW_COMPARATOR = Comparator.comparing(IndirectDraw::stage)
			.thenComparing(IndirectDraw::material, MaterialRenderState.COMPARATOR);

	private static final int DRAW_BARRIER_BITS = GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT;

	private final InstanceType<I> instanceType;
	private final Context context;
	private final long objectStride;
	private final IndirectBuffers buffers;
	private final List<IndirectInstancer<?>> instancers = new ArrayList<>();
	private final List<IndirectDraw> indirectDraws = new ArrayList<>();
	private final Map<RenderStage, List<MultiDraw>> multiDraws = new EnumMap<>(RenderStage.class);

	private final IndirectPrograms programs;
	private final GlProgram cullProgram;
	private final GlProgram applyProgram;
	private final GlProgram drawProgram;

	private boolean needsDrawBarrier;
	private boolean needsDrawSort;
	private int instanceCountThisFrame;

	IndirectCullingGroup(InstanceType<I> instanceType, Context context, IndirectPrograms programs) {
		this.instanceType = instanceType;
		this.context = context;
		objectStride = instanceType.layout()
				.byteSize() + IndirectBuffers.INT_SIZE;
		buffers = new IndirectBuffers(objectStride);

		this.programs = programs;
		// TODO: Culling programs need to be context aware.
		cullProgram = programs.getCullingProgram(instanceType);
		applyProgram = programs.getApplyProgram();
		drawProgram = programs.getIndirectProgram(instanceType, context.contextShader());
	}

	public void flushInstancers() {
		instanceCountThisFrame = 0;
		int modelIndex = 0;
        for (var iterator = instancers.iterator(); iterator.hasNext(); ) {
            var instancer = iterator.next();
            instancer.update();
			var instanceCount = instancer.instanceCount();

			if (instanceCount == 0) {
				iterator.remove();
				for (IndirectDraw draw : instancer.draws()) {
					draw.delete();
				}
				continue;
			}

			instancer.index = modelIndex;
			instancer.baseInstance = instanceCountThisFrame;
			instanceCountThisFrame += instanceCount;

			modelIndex++;
        }

        if (indirectDraws.removeIf(IndirectDraw::deleted)) {
			needsDrawSort = true;
		}
	}

	public void upload(StagingBuffer stagingBuffer) {
		if (nothingToDo()) {
			return;
		}

		buffers.updateCounts(instanceCountThisFrame, instancers.size(), indirectDraws.size());

		// Upload only objects that have changed.
		uploadObjects(stagingBuffer);

		// We need to upload the models every frame to reset the instance count.
		uploadModels(stagingBuffer);

		if (needsDrawSort) {
			sortDraws();
			needsDrawSort = false;
		}

		uploadDraws(stagingBuffer);

		needsDrawBarrier = true;
	}

	public void dispatchCull() {
		if (nothingToDo()) {
			return;
		}

		Uniforms.bindFrame();
		cullProgram.bind();
		buffers.bindForCompute();
		glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
		glDispatchCompute(GlCompat.getComputeGroupCount(instanceCountThisFrame), 1, 1);
	}

	public void dispatchApply() {
		if (nothingToDo()) {
			return;
		}

		applyProgram.bind();
		buffers.bindForCompute();
		glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
		glDispatchCompute(GlCompat.getComputeGroupCount(indirectDraws.size()), 1, 1);
	}

	private boolean nothingToDo() {
		return indirectDraws.isEmpty() || instanceCountThisFrame == 0;
	}

	private boolean nothingToDo(RenderStage stage) {
		return nothingToDo() || !multiDraws.containsKey(stage);
	}

	private void sortDraws() {
		multiDraws.clear();
		// sort by stage, then material
		indirectDraws.sort(DRAW_COMPARATOR);

		for (int start = 0, i = 0; i < indirectDraws.size(); i++) {
			var draw1 = indirectDraws.get(i);
			var material1 = draw1.material();
			var stage1 = draw1.stage();

			// if the next draw call has a different RenderStage or Material, start a new MultiDraw
			if (i == indirectDraws.size() - 1 || stage1 != indirectDraws.get(i + 1)
					.stage() || !material1.equals(indirectDraws.get(i + 1)
					.material())) {
				multiDraws.computeIfAbsent(stage1, s -> new ArrayList<>())
						.add(new MultiDraw(material1, start, i + 1));
				start = i + 1;
			}
		}
	}

	public boolean hasStage(RenderStage stage) {
		return multiDraws.containsKey(stage);
	}

	public void add(IndirectInstancer<I> instancer, Model model, RenderStage stage, MeshPool meshPool) {
        instancer.index = instancers.size();
		instancers.add(instancer);

		for (var entry : model.meshes()) {
			MeshPool.PooledMesh pooledMesh = meshPool.alloc(entry.mesh());
			var draw = new IndirectDraw(instancer, entry.material(), pooledMesh, stage);
			indirectDraws.add(draw);
			instancer.addDraw(draw);
		}

		needsDrawSort = true;
	}

	public void submit(RenderStage stage, TextureSourceImpl textures) {
		if (nothingToDo(stage)) {
			return;
		}

		drawProgram.bind();
		buffers.bindForDraw();

		drawBarrier();

		var flwBaseDraw = drawProgram.getUniformLocation("_flw_baseDraw");

		for (var multiDraw : multiDraws.get(stage)) {
			glUniform1ui(flwBaseDraw, multiDraw.start);

			context.prepare(multiDraw.material, drawProgram, textures);
			MaterialRenderState.setup(multiDraw.material);

			multiDraw.submit();
			TextureBinder.resetTextureBindings();
		}
	}

	public GlProgram bindWithContextShader(SimpleContextShader override) {
		var program = programs.getIndirectProgram(instanceType, override);

		program.bind();

		buffers.bindForCrumbling();

		drawBarrier();

		var flwBaseDraw = drawProgram.getUniformLocation("_flw_baseDraw");
		glUniform1ui(flwBaseDraw, 0);

		return program;
	}

	private void drawBarrier() {
		if (needsDrawBarrier) {
			glMemoryBarrier(DRAW_BARRIER_BITS);
			needsDrawBarrier = false;
		}
	}

	private void uploadObjects(StagingBuffer stagingBuffer) {
		long pos = 0;
		for (var model : instancers) {
			var instanceCount = model.instanceCount();
			model.uploadObjects(stagingBuffer, pos, buffers.object.handle());

			pos += instanceCount * objectStride;
		}
	}

	private void uploadModels(StagingBuffer stagingBuffer) {
		var totalSize = instancers.size() * IndirectBuffers.MODEL_STRIDE;
		var handle = buffers.model.handle();

		stagingBuffer.enqueueCopy(totalSize, handle, 0, this::writeModels);
	}

	private void uploadDraws(StagingBuffer stagingBuffer) {
		var totalSize = indirectDraws.size() * IndirectBuffers.DRAW_COMMAND_STRIDE;
		var handle = buffers.draw.handle();

		stagingBuffer.enqueueCopy(totalSize, handle, 0, this::writeCommands);
	}

	private void writeModels(long writePtr) {
		for (var model : instancers) {
			model.writeModel(writePtr);
			writePtr += IndirectBuffers.MODEL_STRIDE;
		}
	}

	private void writeCommands(long writePtr) {
		for (var draw : indirectDraws) {
			draw.write(writePtr);
			writePtr += IndirectBuffers.DRAW_COMMAND_STRIDE;
		}
	}

	public void delete() {
		buffers.delete();
	}

	private record MultiDraw(Material material, int start, int end) {
		private void submit() {
			if (GlCompat.DRIVER == Driver.INTEL) {
				// Intel renders garbage with MDI, but Consecutive Normal Draws works fine.
				for (int i = this.start; i < this.end; i++) {
					glDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, i * IndirectBuffers.DRAW_COMMAND_STRIDE);
				}
			} else {
				glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, this.start * IndirectBuffers.DRAW_COMMAND_STRIDE, this.end - this.start, (int) IndirectBuffers.DRAW_COMMAND_STRIDE);
			}
		}
	}
}
