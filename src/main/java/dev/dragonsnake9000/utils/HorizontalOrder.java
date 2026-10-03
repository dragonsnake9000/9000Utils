package dev.dragonsnake9000.utils;

/** Rectangular rings: every edge, including the short sides of a long region, ranks as outer. */
final class HorizontalOrder {
    static double fraction(int x, int z, int x1, int z1, int x2, int z2) {
        double cx = ((double) x1 + x2) / 2, cz = ((double) z1 + z2) / 2;
        double rx = Math.max(.5, Math.abs((double) x2 - x1) / 2);
        double rz = Math.max(.5, Math.abs((double) z2 - z1) / 2);
        return Math.min(1, Math.max(Math.abs(x - cx) / rx, Math.abs(z - cz) / rz));
    }
}
