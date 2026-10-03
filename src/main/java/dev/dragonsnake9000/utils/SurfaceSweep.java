package dev.dragonsnake9000.utils;

/** Stable height bands and clockwise continuation prevent switching between opposite height peaks. */
final class SurfaceSweep {
    private boolean active;
    private int anchor, sign, width;
    private double centerX, centerZ, angle;
    boolean active() { return active; }
    double centerX() { return centerX; }
    double centerZ() { return centerZ; }
    void reset() { active = false; }
    void begin(int y, int order, int band, double x, double z, double playerX, double playerZ) {
        active = true; anchor = y; sign = order; width = Math.max(1, band);
        centerX = x; centerZ = z;
        angle = Math.atan2(playerZ - z, playerX - x);
    }
    boolean accepts(int y) { return !active || (sign < 0 ? y <= anchor && y > anchor - width : y >= anchor && y < anchor + width); }
    boolean ahead(int y) { return !active || (sign < 0 ? y <= anchor - width : y >= anchor + width); }
    double rank(double x, double z, double distanceScore) {
        if (!active) return distanceScore;
        double delta = forwardAngle(angle, Math.atan2(z - centerZ, x - centerX));
        double radius = Math.max(2, Math.hypot(x - centerX, z - centerZ));
        return distanceScore + delta * radius * .8;
    }
    void selected(double x, double z) { angle = Math.atan2(z - centerZ, x - centerX); }
    static double forwardAngle(double from, double to) {
        double delta = (to - from) % (Math.PI * 2);
        if (delta < 0) delta += Math.PI * 2;
        return delta;
    }
}
