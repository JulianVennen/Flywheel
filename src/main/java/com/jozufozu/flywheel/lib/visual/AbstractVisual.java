package com.jozufozu.flywheel.lib.visual;

import java.util.Objects;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.jozufozu.flywheel.api.instance.InstancerProvider;
import com.jozufozu.flywheel.api.visual.Visual;
import com.jozufozu.flywheel.api.visualization.VisualizationContext;
import com.jozufozu.flywheel.lib.instance.FlatLit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

public abstract class AbstractVisual implements Visual {
	/**
	 * The visualization context used to construct this visual.
	 * <br>
	 * Useful for passing to child visuals.
	 */
	protected final VisualizationContext visualizationContext;
	protected final InstancerProvider instancerProvider;
	protected final Vec3i renderOrigin;
	protected final Level level;

	protected boolean deleted = false;

	public AbstractVisual(VisualizationContext ctx, Level level) {
		this.visualizationContext = ctx;
		this.instancerProvider = ctx.instancerProvider();
		this.renderOrigin = ctx.renderOrigin();
		this.level = level;
	}

	@Override
	public void update(float partialTick) {
	}

	@Override
	public boolean shouldReset() {
		return false;
	}

	protected abstract void _delete();

	@Override
	public final void delete() {
		if (deleted) {
			return;
		}

		_delete();
		deleted = true;
	}

	protected void relight(BlockPos pos, @Nullable FlatLit... instances) {
		relight(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos), instances);
	}

	protected void relight(int block, int sky, @Nullable FlatLit... instances) {
		for (FlatLit instance : instances) {
			if (instance == null) {
				continue;
			}

			instance.setLight(block, sky);
			instance.handle()
					.setChanged();
		}
	}

	protected void relight(BlockPos pos, Stream<? extends @Nullable FlatLit> instances) {
		relight(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos), instances);
	}

	protected void relight(int block, int sky, Stream<? extends @Nullable FlatLit> instances) {
		instances.filter(Objects::nonNull)
				.forEach(instance -> instance.setLight(block, sky)
				.handle()
				.setChanged());
	}

	protected void relight(BlockPos pos, Iterable<? extends @Nullable FlatLit> instances) {
		relight(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos), instances);
	}

	protected void relight(int block, int sky, Iterable<? extends @Nullable FlatLit> instances) {
		for (FlatLit instance : instances) {
			if (instance == null) {
				continue;
			}
			instance.setLight(block, sky)
					.handle()
					.setChanged();
		}
	}
}
