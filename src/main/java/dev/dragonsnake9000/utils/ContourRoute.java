package dev.dragonsnake9000.utils;

/** Immutable route corridor shared with A*: keep contour travel outside the cone's interior. */
public record ContourRoute(double centerX, double centerZ, double innerRadius, int ceiling) {
    public static volatile ContourRoute current;
    public static boolean allows(int x, int y, int z, int nx, int ny, int nz) {
        ContourRoute route = current;
        return route == null || route.edge(x + .5, y, z + .5, nx + .5, ny, nz + .5);
    }
    boolean edge(double x, int y, double z, double nx, int ny, double nz) {
        if (ny > ceiling && ny > y) return false;
        double sx = x - centerX, sz = z - centerZ, dx = nx - x, dz = nz - z;
        double before = Math.hypot(sx, sz), after = Math.hypot(nx - centerX, nz - centerZ);
        if (before < innerRadius) return after >= before;
        double length = dx * dx + dz * dz;
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, -(sx * dx + sz * dz) / length));
        return Math.hypot(sx + t * dx, sz + t * dz) >= innerRadius;
    }
}
