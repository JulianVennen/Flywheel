package com.jozufozu.flywheel.glsl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import com.jozufozu.flywheel.lib.util.ResourceUtil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The main object for loading and parsing source files.
 */
public class ShaderSources {
	public static final String SHADER_DIR = "flywheel/";

	private final ResourceManager manager;

	@VisibleForTesting
	protected final Map<ResourceLocation, LoadResult> cache = new HashMap<>();

	/**
	 * Tracks where we are in the mutual recursion to detect circular imports.
	 */
	private final Deque<ResourceLocation> findStack = new ArrayDeque<>();

	public ShaderSources(ResourceManager manager) {
		this.manager = manager;
	}

	@NotNull
	public LoadResult find(ResourceLocation location) {
		if (findStack.contains(location)) {
			// Make a copy of the find stack with the offending location added on top to show the full path.
			findStack.addLast(location);
			var copy = List.copyOf(findStack);
			findStack.removeLast();
			return new LoadResult.Failure(new LoadError.CircularDependency(location, copy));
		}
		findStack.addLast(location);

		LoadResult out = _find(location);

		findStack.removeLast();
		return out;
	}

	@NotNull
	private LoadResult _find(ResourceLocation location) {
		// Can't use computeIfAbsent because mutual recursion causes ConcurrentModificationExceptions
		var out = cache.get(location);
		if (out == null) {
			out = load(location);
			cache.put(location, out);
		}
		return out;
	}

	@NotNull
	protected LoadResult load(ResourceLocation loc) {
		return manager.getResource(ResourceUtil.prefixed(SHADER_DIR, loc))
				.map(resource -> {
					try (InputStream stream = resource.open()) {
						String sourceString = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
						return SourceFile.parse(this, loc, sourceString);
					} catch (IOException e) {
						return new LoadResult.Failure(new LoadError.IOError(loc, e));
					}
				})
				.orElseGet(() -> new LoadResult.Failure(new LoadError.ResourceError(loc)));
	}
}
