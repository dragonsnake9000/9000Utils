package dev.dragonsnake9000.utils;

import net.minecraft.util.math.Vec3i;

/** Coordinate comparison for non-BlockPos arguments passed to Baritone by render mods. */
public final class PositionCompatibility {
    public static boolean matches(int x, int y, int z, Object other) {
        return other instanceof Vec3i vector && x == vector.getX() && y == vector.getY() && z == vector.getZ();
    }
}
