package dev.dragonsnake9000.utils;

/** Immutable bounds use inclusive player-feet block coordinates. Safe on Baritone's worker thread. */
public record RegionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static RegionBounds normalized(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new RegionBounds(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
            Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
    public int clampX(int x) { return Math.max(minX, Math.min(maxX, x)); }
    public int clampY(int y) { return Math.max(minY, Math.min(maxY, y)); }
    public int clampZ(int z) { return Math.max(minZ, Math.min(maxZ, z)); }
    public double distanceSquared(int x, int y, int z) {
        double dx = (double) x - clampX(x), dy = (double) y - clampY(y), dz = (double) z - clampZ(z);
        return dx * dx + dy * dy + dz * dz;
    }
}
