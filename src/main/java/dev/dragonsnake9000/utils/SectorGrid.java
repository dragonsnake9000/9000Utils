package dev.dragonsnake9000.utils;

/** The map is north-up: A-J increase X, rows 1-10 increase Z. */
final class SectorGrid {
    static String name(int index) { return "" + (char) ('A' + index % 10) + (index / 10 + 1); }
    static int at(double x, double z) {
        if (x < -5000 || x >= 5000 || z < -5000 || z >= 5000) return -1;
        return (int) Math.floor((z + 5000) / 1000) * 10 + (int) Math.floor((x + 5000) / 1000);
    }
    static int minX(int index) { return -5000 + index % 10 * 1000; }
    static int minZ(int index) { return -5000 + index / 10 * 1000; }
    static RegionBounds bounds(int index, int y1, int y2) {
        // Inclusive block bounds: 1000 original columns plus five on either side.
        return RegionBounds.normalized(minX(index)-5,y1,minZ(index)-5,minX(index)+1004,y2,minZ(index)+1004);
    }
    static int plane(double cameraY) { return 600; }
}
