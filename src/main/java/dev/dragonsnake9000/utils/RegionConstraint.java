package dev.dragonsnake9000.utils;

/** Published by the client; read by search/execution mixins without touching client world state. */
public final class RegionConstraint {
    public static volatile RegionBounds bounds;
    public static volatile RegionBounds entryCorridor;
    public static boolean allows(int x, int y, int z) {
        RegionBounds current = bounds;
        RegionBounds corridor = entryCorridor;
        return current == null || current.contains(x, y, z) || (corridor != null && corridor.contains(x, y, z));
    }
}
