package com.jozufozu.flywheel.backend.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL32;

import com.jozufozu.flywheel.api.model.Mesh;
import com.jozufozu.flywheel.api.vertex.VertexView;
import com.jozufozu.flywheel.backend.InternalVertex;
import com.jozufozu.flywheel.backend.gl.GlPrimitive;
import com.jozufozu.flywheel.backend.gl.array.GlVertexArray;
import com.jozufozu.flywheel.backend.gl.buffer.GlBuffer;
import com.jozufozu.flywheel.lib.memory.MemoryBlock;

public class MeshPool {
	private final VertexView vertexView;
	private final Map<Mesh, PooledMesh> meshes = new HashMap<>();
	private final List<PooledMesh> meshList = new ArrayList<>();
	private final List<PooledMesh> recentlyAllocated = new ArrayList<>();

	private final GlBuffer vbo;
	private final IndexPool indexPool;

	private boolean dirty;
	private boolean anyToRemove;

	/**
	 * Create a new mesh pool.
	 */
	public MeshPool() {
		vertexView = InternalVertex.createVertexView();
		vbo = new GlBuffer();
		indexPool = new IndexPool();
	}

	/**
	 * Allocate a model in the arena.
	 *
	 * @param mesh The model to allocate.
	 * @return A handle to the allocated model.
	 */
	public PooledMesh alloc(Mesh mesh) {
		return meshes.computeIfAbsent(mesh, this::_alloc);
	}

	private PooledMesh _alloc(Mesh m) {
		PooledMesh bufferedModel = new PooledMesh(m);
		meshList.add(bufferedModel);
		recentlyAllocated.add(bufferedModel);

		dirty = true;
		return bufferedModel;
	}

	@Nullable
	public MeshPool.PooledMesh get(Mesh mesh) {
		return meshes.get(mesh);
	}

	public void flush() {
        if (!dirty) {
            return;
        }

		if (anyToRemove) {
			anyToRemove = false;
			processDeletions();

			// Might want to shrink the index pool if something was removed.
			indexPool.reset();
			for (PooledMesh mesh : meshList) {
				indexPool.updateCount(mesh.mesh.indexSequence(), mesh.indexCount());
			}
		} else {
			// Otherwise, just update the index with the new counts.
			for (PooledMesh mesh : recentlyAllocated) {
				indexPool.updateCount(mesh.mesh.indexSequence(), mesh.indexCount());
			}
			recentlyAllocated.clear();
		}

		// Always need to flush the index pool.
		indexPool.flush();

		uploadAll();
        dirty = false;
    }

	private void processDeletions() {
		// remove deleted meshes
		meshList.removeIf(pooledMesh -> {
			boolean deleted = pooledMesh.deleted();
			if (deleted) {
				meshes.remove(pooledMesh.mesh);
			}
			return deleted;
		});
	}

	private void uploadAll() {
		long neededSize = 0;
		for (PooledMesh mesh : meshList) {
			neededSize += mesh.byteSize();
		}

		final var vertexBlock = MemoryBlock.malloc(neededSize);
		final long vertexPtr = vertexBlock.ptr();

		int byteIndex = 0;
		int baseVertex = 0;
		for (PooledMesh mesh : meshList) {
			mesh.baseVertex = baseVertex;

			vertexView.ptr(vertexPtr + byteIndex);
			vertexView.vertexCount(mesh.vertexCount());
			mesh.mesh.write(vertexView);

			byteIndex += mesh.byteSize();
			baseVertex += mesh.vertexCount();
		}

		vbo.upload(vertexBlock);

		vertexBlock.free();
	}

	public void bind(GlVertexArray vertexArray) {
		indexPool.bind(vertexArray);
		vertexArray.bindVertexBuffer(0, vbo.handle(), 0, InternalVertex.STRIDE);
		vertexArray.bindAttributes(0, 0, InternalVertex.ATTRIBUTES);
	}

	public void delete() {
		vbo.delete();
		indexPool.delete();
		meshes.clear();
		meshList.clear();
	}

	public class PooledMesh {
		public static final int INVALID_BASE_VERTEX = -1;
		private final Mesh mesh;

		private int baseVertex = INVALID_BASE_VERTEX;

		private int referenceCount = 0;

		private PooledMesh(Mesh mesh) {
			this.mesh = mesh;
		}

		public int vertexCount() {
			return mesh.vertexCount();
		}

		public int byteSize() {
			return mesh.vertexCount() * InternalVertex.STRIDE;
		}

		public int indexCount() {
			return mesh.indexCount();
		}

		public int baseVertex() {
			return baseVertex;
		}

		public int firstIndex() {
			return MeshPool.this.indexPool.firstIndex(mesh.indexSequence());
		}

		public long firstIndexByteOffset() {
			return (long) firstIndex() * Integer.BYTES;
		}

		public boolean deleted() {
			return referenceCount <= 0;
		}

		public boolean invalid() {
			return mesh.vertexCount() == 0 || baseVertex == INVALID_BASE_VERTEX || deleted();
		}

		public void draw(int instanceCount) {
			if (instanceCount > 1) {
				GL32.glDrawElementsInstancedBaseVertex(GlPrimitive.TRIANGLES.glEnum, mesh.indexCount(), GL32.GL_UNSIGNED_INT, firstIndexByteOffset(), instanceCount, baseVertex);
			} else {
				GL32.glDrawElementsBaseVertex(GlPrimitive.TRIANGLES.glEnum, mesh.indexCount(), GL32.GL_UNSIGNED_INT, firstIndexByteOffset(), baseVertex);
			}
		}

		public void acquire() {
			referenceCount++;
		}

		public void release() {
			if (--referenceCount == 0) {
				MeshPool.this.dirty = true;
				MeshPool.this.anyToRemove = true;
			}
		}
	}
}
