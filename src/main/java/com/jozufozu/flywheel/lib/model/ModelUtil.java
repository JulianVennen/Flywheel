package com.jozufozu.flywheel.lib.model;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collection;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import com.dreizak.miniball.highdim.Miniball;
import com.dreizak.miniball.model.PointSet;
import com.jozufozu.flywheel.api.material.Material;
import com.jozufozu.flywheel.api.model.Mesh;
import com.jozufozu.flywheel.api.vertex.ReusableVertexList;
import com.jozufozu.flywheel.api.vertex.VertexList;
import com.jozufozu.flywheel.api.vertex.VertexListProviderRegistry;
import com.jozufozu.flywheel.api.vertex.VertexType;
import com.jozufozu.flywheel.lib.material.Materials;
import com.jozufozu.flywheel.lib.memory.MemoryBlock;
import com.jozufozu.flywheel.lib.vertex.PositionOnlyVertexList;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder.DrawState;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

public final class ModelUtil {
	private static final Logger LOGGER = LogUtils.getLogger();

	/**
	 * An alternative BlockRenderDispatcher that circumvents the Forge rendering pipeline to ensure consistency.
	 * Meant to be used for virtual rendering.
	 */
	public static final BlockRenderDispatcher VANILLA_RENDERER = createVanillaRenderer();

	private ModelUtil() {
	}

	private static BlockRenderDispatcher createVanillaRenderer() {
		BlockRenderDispatcher defaultDispatcher = Minecraft.getInstance().getBlockRenderer();
		BlockRenderDispatcher dispatcher = new BlockRenderDispatcher(null, null, null);
		try {
			for (Field field : BlockRenderDispatcher.class.getDeclaredFields()) {
				field.setAccessible(true);
				field.set(dispatcher, field.get(defaultDispatcher));
			}
			ObfuscationReflectionHelper.setPrivateValue(BlockRenderDispatcher.class, dispatcher, new ModelBlockRenderer(Minecraft.getInstance().getBlockColors()), "f_110900_");
		} catch (Exception e) {
			LOGGER.error("Failed to initialize vanilla BlockRenderDispatcher!", e);
			return defaultDispatcher;
		}
		return dispatcher;
	}

	public static MemoryBlock convertVanillaBuffer(BufferBuilder.RenderedBuffer buffer, VertexType vertexType) {
		DrawState drawState = buffer.drawState();
		int vertexCount = drawState.vertexCount();
		VertexFormat srcFormat = drawState.format();

		ByteBuffer src = buffer.vertexBuffer();
		MemoryBlock dst = MemoryBlock.malloc((long) vertexCount * vertexType.getStride());
		long srcPtr = MemoryUtil.memAddress(src);
		long dstPtr = dst.ptr();

		ReusableVertexList srcList = VertexListProviderRegistry.getProvider(srcFormat).createVertexList();
		ReusableVertexList dstList = vertexType.createVertexList();
		srcList.ptr(srcPtr);
		dstList.ptr(dstPtr);
		srcList.vertexCount(vertexCount);
		dstList.vertexCount(vertexCount);

		srcList.writeAll(dstList);

		return dst;
	}

	@Nullable
	public static Material getMaterial(RenderType chunkRenderType, boolean shaded) {
		if (chunkRenderType == RenderType.solid()) {
			return shaded ? Materials.CHUNK_SOLID_SHADED : Materials.CHUNK_SOLID_UNSHADED;
		}
		if (chunkRenderType == RenderType.cutoutMipped()) {
			return shaded ? Materials.CHUNK_CUTOUT_MIPPED_SHADED : Materials.CHUNK_CUTOUT_MIPPED_UNSHADED;
		}
		if (chunkRenderType == RenderType.cutout()) {
			return shaded ? Materials.CHUNK_CUTOUT_SHADED : Materials.CHUNK_CUTOUT_UNSHADED;
		}
		if (chunkRenderType == RenderType.translucent()) {
			return shaded ? Materials.CHUNK_TRANSLUCENT_SHADED : Materials.CHUNK_TRANSLUCENT_UNSHADED;
		}
		if (chunkRenderType == RenderType.tripwire()) {
			return shaded ? Materials.CHUNK_TRIPWIRE_SHADED : Materials.CHUNK_TRIPWIRE_UNSHADED;
		}
		return null;
	}

	public static Vector4f computeBoundingSphere(Collection<Mesh> values) {
		int totalVertices = 0;
		for (Mesh value : values) {
			totalVertices += value.vertexCount();
		}
		var block = MemoryBlock.malloc((long) totalVertices * PositionOnlyVertexList.STRIDE);

		var vertexList = new PositionOnlyVertexList();

		int baseVertex = 0;
		for (Mesh value : values) {
			vertexList.ptr(block.ptr() + (long) baseVertex * PositionOnlyVertexList.STRIDE);
			value.write(vertexList);
			baseVertex += value.vertexCount();
		}

		vertexList.ptr(block.ptr());
		vertexList.vertexCount(totalVertices);

		var out = computeBoundingSphere(vertexList);

		block.free();
		return out;
	}

	public static Vector4f computeBoundingSphere(VertexList vertexList) {
		return computeBoundingSphere(new PointSet() {
			@Override
			public int size() {
				return vertexList.vertexCount();
			}

			@Override
			public int dimension() {
				return 3;
			}

			@Override
			public double coord(int i, int dim) {
				return switch (dim) {
					case 0 -> vertexList.x(i);
					case 1 -> vertexList.y(i);
					case 2 -> vertexList.z(i);
					default -> throw new IllegalArgumentException("Invalid dimension: " + dim);
				};
			}
		});
	}

	public static Vector4f computeBoundingSphere(PointSet points) {
		var miniball = new Miniball(points);
		double[] center = miniball.center();
		double radius = miniball.radius();
		return new Vector4f((float) center[0], (float) center[1], (float) center[2], (float) radius);
	}
}
