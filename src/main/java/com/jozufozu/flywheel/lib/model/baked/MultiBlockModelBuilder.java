package com.jozufozu.flywheel.lib.model.baked;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiFunction;

import com.google.common.collect.ImmutableList;
import com.jozufozu.flywheel.api.material.Material;
import com.jozufozu.flywheel.api.model.Model;
import com.jozufozu.flywheel.api.vertex.VertexView;
import com.jozufozu.flywheel.lib.memory.MemoryBlock;
import com.jozufozu.flywheel.lib.model.ModelUtil;
import com.jozufozu.flywheel.lib.model.SimpleMesh;
import com.jozufozu.flywheel.lib.model.baked.BakedModelBufferer.ResultConsumer;
import com.jozufozu.flywheel.lib.model.baked.BakedModelBufferer.ShadeSeparatedResultConsumer;
import com.jozufozu.flywheel.lib.vertex.NoOverlayVertexView;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.client.model.data.ModelData;

public class MultiBlockModelBuilder {
	private final Collection<StructureTemplate.StructureBlockInfo> blocks;
	private boolean shadeSeparated = true;
	private BlockAndTintGetter renderWorld;
	private PoseStack poseStack;
	private Map<BlockPos, ModelData> modelDataMap;
	private BiFunction<RenderType, Boolean, Material> materialFunc;

	public MultiBlockModelBuilder(Collection<StructureTemplate.StructureBlockInfo> blocks) {
		this.blocks = blocks;
	}

	public MultiBlockModelBuilder disableShadeSeparation() {
		shadeSeparated = false;
		return this;
	}

	public MultiBlockModelBuilder renderWorld(BlockAndTintGetter renderWorld) {
		this.renderWorld = renderWorld;
		return this;
	}

	public MultiBlockModelBuilder poseStack(PoseStack poseStack) {
		this.poseStack = poseStack;
		return this;
	}

	public MultiBlockModelBuilder modelDataMap(Map<BlockPos, ModelData> modelDataMap) {
		this.modelDataMap = modelDataMap;
		return this;
	}

	public MultiBlockModelBuilder materialFunc(BiFunction<RenderType, Boolean, Material> materialFunc) {
		this.materialFunc = materialFunc;
		return this;
	}

	public TessellatedModel build() {
		if (renderWorld == null) {
			renderWorld = VirtualEmptyBlockGetter.INSTANCE;
		}
		if (modelDataMap == null) {
			modelDataMap = Collections.emptyMap();
		}
		if (materialFunc == null) {
			materialFunc = ModelUtil::getMaterial;
		}

		var out = ImmutableList.<Model.ConfiguredMesh>builder();

		if (shadeSeparated) {
			ShadeSeparatedResultConsumer resultConsumer = (renderType, shaded, data) -> {
				Material material = materialFunc.apply(renderType, shaded);
				if (material != null) {
					VertexView vertexView = new NoOverlayVertexView();
					MemoryBlock meshData = ModelUtil.convertVanillaBuffer(data, vertexView);
					var mesh = new SimpleMesh(vertexView, meshData, "source=MultiBlockModelBuilder," + "renderType=" + renderType + ",shaded=" + shaded);
					out.add(new Model.ConfiguredMesh(material, mesh));
				}
			};
			BakedModelBufferer.bufferMultiBlockShadeSeparated(blocks, ModelUtil.VANILLA_RENDERER, renderWorld, poseStack, modelDataMap, resultConsumer);
		} else {
			ResultConsumer resultConsumer = (renderType, data) -> {
				Material material = materialFunc.apply(renderType, true);
				if (material != null) {
					VertexView vertexView = new NoOverlayVertexView();
					MemoryBlock meshData = ModelUtil.convertVanillaBuffer(data, vertexView);
					var mesh = new SimpleMesh(vertexView, meshData, "source=MultiBlockModelBuilder," + "renderType=" + renderType);
					out.add(new Model.ConfiguredMesh(material, mesh));
				}
			};
			BakedModelBufferer.bufferMultiBlock(blocks, ModelUtil.VANILLA_RENDERER, renderWorld, poseStack, modelDataMap, resultConsumer);
		}

		return new TessellatedModel(out.build(), shadeSeparated);
	}
}
