package com.jozufozu.flywheel.lib.math;

import net.minecraftforge.client.model.lighting.QuadLighter;

public final class RenderMath {
	private RenderMath() {
	}

	/**
	 * Convert a signed byte into a signed, normalized float.
	 */
	public static float f(byte b) {
		return b / 127f;
	}

	/**
	 * Convert a signed, normalized float into a signed byte.
	 */
	public static byte nb(float f) {
		return (byte) (f * 127);
	}

	/**
	 * Convert an unsigned byte into an unsigned, normalized float.
	 */
	public static float uf(byte b) {
		return (float) (Byte.toUnsignedInt(b)) / 255f;
	}

	/**
	 * Convert an unsigned, normalized float into an unsigned byte.
	 */
	public static byte unb(float f) {
		return (byte) (int) (f * 255);
	}

	public static float diffuseLight(float x, float y, float z, boolean shaded) {
		if (!shaded) {
			return 1f;
		}
		return QuadLighter.calculateShade(x, y, z, false);
	}

	public static float diffuseLightNether(float x, float y, float z, boolean shaded) {
		if (!shaded) {
			return 0.9f;
		}
		return QuadLighter.calculateShade(x, y, z, true);
	}
}
