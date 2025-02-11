package com.jozufozu.flywheel.impl;

import org.slf4j.Logger;

import com.jozufozu.flywheel.Flywheel;
import com.jozufozu.flywheel.api.backend.Backend;
import com.jozufozu.flywheel.api.event.EndClientResourceReloadEvent;
import com.jozufozu.flywheel.api.event.ReloadLevelRendererEvent;
import com.jozufozu.flywheel.backend.Backends;
import com.jozufozu.flywheel.config.FlwConfig;
import com.jozufozu.flywheel.impl.visualization.VisualizationManagerImpl;
import com.jozufozu.flywheel.lib.backend.SimpleBackend;
import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.CrashReportCallables;

public final class BackendManagerImpl {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static final Backend OFF_BACKEND = SimpleBackend.builder()
			.engineMessage(Component.literal("Disabled Flywheel")
					.withStyle(ChatFormatting.RED))
			.engineFactory(level -> {
				throw new UnsupportedOperationException("Cannot create engine when backend is off.");
			})
			.supported(() -> true)
			.register(Flywheel.rl("off"));

	public static final Backend DEFAULT_BACKEND = findDefaultBackend();

	private static Backend backend = OFF_BACKEND;

	private BackendManagerImpl() {
	}

	public static Backend getBackend() {
		return backend;
	}

	public static boolean isBackendOn() {
		return backend != OFF_BACKEND;
	}

	private static Backend findDefaultBackend() {
		// TODO: Automatically select the best default config based on the user's driver
		// TODO: Figure out how this will work if custom backends are registered and without hardcoding the default backends
		return Backends.INDIRECT;
	}

	private static void chooseBackend() {
		var preferred = FlwConfig.get().getBackend();
		var actual = preferred.findFallback();

		if (preferred != actual) {
			LOGGER.warn("Flywheel backend fell back from '{}' to '{}'", Backend.REGISTRY.getIdOrThrow(preferred), Backend.REGISTRY.getIdOrThrow(actual));
		}

		backend = actual;
	}

	public static String getBackendString() {
		ResourceLocation backendId = Backend.REGISTRY.getId(backend);
		if (backendId == null) {
			return "[unregistered]";
		}
		return backendId.toString();
	}

	public static void init() {
		CrashReportCallables.registerCrashCallable("Flywheel Backend", BackendManagerImpl::getBackendString);
	}

	public static void onEndClientResourceReload(EndClientResourceReloadEvent event) {
		if (event.getError().isPresent()) {
			return;
		}

		chooseBackend();
		VisualizationManagerImpl.resetAll();
	}

	public static void onReloadLevelRenderer(ReloadLevelRendererEvent event) {
		chooseBackend();

		ClientLevel level = event.getLevel();
		if (level != null) {
			VisualizationManagerImpl.reset(level);
		}
	}
}
