package com.jozufozu.flywheel.backend.compile;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;
import com.jozufozu.flywheel.Flywheel;
import com.jozufozu.flywheel.api.context.Context;
import com.jozufozu.flywheel.api.instance.InstanceType;
import com.jozufozu.flywheel.api.vertex.VertexType;
import com.jozufozu.flywheel.backend.compile.component.MaterialAdapterComponent;
import com.jozufozu.flywheel.backend.compile.component.UniformComponent;
import com.jozufozu.flywheel.gl.shader.GlProgram;

public class IndirectPrograms {
	public static IndirectPrograms instance;
	private final Map<PipelineProgramKey, GlProgram> pipeline;
	private final Map<InstanceType<?>, GlProgram> culling;

	public IndirectPrograms(Map<PipelineProgramKey, GlProgram> pipeline, Map<InstanceType<?>, GlProgram> culling) {
		this.pipeline = pipeline;
		this.culling = culling;
	}

	static void reload(SourceLoader loadChecker, ImmutableList<PipelineProgramKey> pipelineKeys, UniformComponent uniformComponent, MaterialAdapterComponent vertexMaterialComponent, MaterialAdapterComponent fragmentMaterialComponent) {
		_delete();
		var pipelineCompiler = PipelineCompiler.create(loadChecker, Pipelines.INDIRECT, pipelineKeys, uniformComponent, vertexMaterialComponent, fragmentMaterialComponent);
		var cullingCompiler = CullingCompiler.create(loadChecker, createCullingKeys(), uniformComponent);

		try {
			var pipelineResult = pipelineCompiler.compileAndReportErrors();
			var cullingResult = cullingCompiler.compileAndReportErrors();

			if (pipelineResult != null && cullingResult != null) {
				instance = new IndirectPrograms(pipelineResult, cullingResult);
			}
		} catch (Throwable e) {
			Flywheel.LOGGER.error("Failed to compile indirect programs", e);
		}
		pipelineCompiler.delete();
		cullingCompiler.delete();
	}

	private static ImmutableList<InstanceType<?>> createCullingKeys() {
		ImmutableList.Builder<InstanceType<?>> builder = ImmutableList.builder();
		for (InstanceType<?> instanceType : InstanceType.REGISTRY) {
			builder.add(instanceType);
		}
		return builder.build();
	}

	@Nullable
	public static IndirectPrograms get() {
		return instance;
	}

	public static boolean allLoaded() {
		return instance != null;
	}

	private static void _delete() {
		if (instance != null) {
			instance.delete();
			instance = null;
		}
	}

	public GlProgram getIndirectProgram(VertexType vertexType, InstanceType<?> instanceType, Context contextShader) {
		return pipeline.get(new PipelineProgramKey(vertexType, instanceType, contextShader));
	}

	public GlProgram getCullingProgram(InstanceType<?> instanceType) {
		return culling.get(instanceType);
	}

	public void delete() {
		pipeline.values()
				.forEach(GlProgram::delete);
		culling.values()
				.forEach(GlProgram::delete);
	}
}
