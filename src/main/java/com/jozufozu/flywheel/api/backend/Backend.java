package com.jozufozu.flywheel.api.backend;

import com.jozufozu.flywheel.api.BackendImplemented;
import com.jozufozu.flywheel.api.internal.InternalFlywheelApi;
import com.jozufozu.flywheel.api.registry.IdRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelAccessor;

@BackendImplemented
public interface Backend {
	static IdRegistry<Backend> REGISTRY = InternalFlywheelApi.INSTANCE.createIdRegistry();

	/**
	 * Get a message to display to the user when the engine is enabled.
	 */
	Component engineMessage();

	/**
	 * Create a new engine instance.
	 */
	Engine createEngine(LevelAccessor level);

	/**
	 * Get a fallback backend in case this backend is not supported.
	 */
	Backend findFallback();

	/**
	 * Check if this backend is supported.
	 */
	boolean isSupported();
}
