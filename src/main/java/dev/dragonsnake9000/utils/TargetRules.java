package dev.dragonsnake9000.utils;

import java.util.function.IntPredicate;

/** World-independent clearance rule, shared by scanning and revalidation. */
final class TargetRules {
    static boolean permitsCover(int offset, boolean air, boolean snowLayer, boolean allowSnow) {
        return air || (allowSnow && offset == 1 && snowLayer);
    }
    static boolean hasClearance(int y, int topExclusive, int required, IntPredicate airAtY) {
        if (required < 0) return false;
        int clearance = Math.max(1, required);
        if ((long) y + clearance >= topExclusive) return false;
        for (int offset = 1; offset <= clearance; offset++) {
            if (!airAtY.test(y + offset)) return false;
        }
        return true;
    }

    @FunctionalInterface interface Neighbor { boolean isCobblestone(int dx, int dy, int dz); }
    static int countNeighbors(Neighbor neighbor) {
        int count = 0;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if ((x != 0 || y != 0 || z != 0) && neighbor.isCobblestone(x, y, z)) count++;
        }
        return count;
    }
    static double score(double distance, int neighbors, double preference) {
        return distance / (1 + Math.max(0, preference) * Math.min(26, Math.max(0, neighbors)) / 26.0);
    }
    static boolean fallbackReady(long now, long emptySince, int delayTicks) {
        return emptySince >= 0 && now - emptySince >= delayTicks;
    }
}
