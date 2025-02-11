package com.jozufozu.flywheel.impl.mixin.visualmanage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jozufozu.flywheel.impl.visualization.VisualizationHelper;

import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$RebuildTask")
abstract class RebuildTaskMixin {
	@Inject(method = "handleBlockEntity(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection$RebuildTask$CompileResults;Lnet/minecraft/world/level/block/entity/BlockEntity;)V", at = @At("HEAD"), cancellable = true)
	private void flywheel$tryAddBlockEntity(@Coerce Object compileResults, BlockEntity blockEntity, CallbackInfo ci) {
		if (VisualizationHelper.tryAddBlockEntity(blockEntity)) {
			ci.cancel();
		}
	}
}
