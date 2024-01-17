package com.jozufozu.flywheel.backend.compile;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;
import com.jozufozu.flywheel.Flywheel;
import com.jozufozu.flywheel.api.context.Context;
import com.jozufozu.flywheel.api.instance.InstanceType;
import com.jozufozu.flywheel.backend.compile.component.UniformComponent;
import com.jozufozu.flywheel.backend.gl.shader.GlProgram;
import com.jozufozu.flywheel.backend.glsl.ShaderSources;
import com.jozufozu.flywheel.backend.glsl.SourceComponent;

public class InstancingPrograms extends AbstractPrograms {
	@Nullable
	private static InstancingPrograms instance;

	private final Map<PipelineProgramKey, GlProgram> pipeline;

	private InstancingPrograms(Map<PipelineProgramKey, GlProgram> pipeline) {
		this.pipeline = pipeline;
	}

	static void reload(ShaderSources sources, ImmutableList<PipelineProgramKey> pipelineKeys, UniformComponent uniformComponent, List<SourceComponent> vertexComponents, List<SourceComponent> fragmentComponents) {
		InstancingPrograms newInstance = null;

		var pipelineCompiler = PipelineCompiler.create(sources, Pipelines.INSTANCED_ARRAYS, uniformComponent, vertexComponents, fragmentComponents);

		try {
			var pipelineResult = pipelineCompiler.compileAndReportErrors(pipelineKeys);

			if (pipelineResult != null) {
				newInstance = new InstancingPrograms(pipelineResult);
			}
		} catch (Throwable t) {
			Flywheel.LOGGER.error("Failed to compile instancing programs", t);
		}

		pipelineCompiler.delete();

		setInstance(newInstance);
	}

	private static void setInstance(@Nullable InstancingPrograms newInstance) {
		if (instance != null) {
			instance.release();
		}
		if (newInstance != null) {
			newInstance.acquire();
		}
		instance = newInstance;
	}

	@Nullable
	public static InstancingPrograms get() {
		return instance;
	}

	public static boolean allLoaded() {
		return instance != null;
	}

	public GlProgram get(InstanceType<?> instanceType, Context contextShader) {
		return pipeline.get(new PipelineProgramKey(instanceType, contextShader));
	}

	@Override
	protected void delete() {
		pipeline.values()
				.forEach(GlProgram::delete);
	}
}
