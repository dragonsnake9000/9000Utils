package dev.dragonsnake9000.utils;

/** Conservative quick-move budget; retain an empty slot unless all remaining moss fits. */
final class RefillPlanner {
    static boolean canTakeStack(int stackCount, int mossRemaining, int emptySlots, int mergeSpace) {
        if (stackCount <= 0 || mossRemaining < stackCount || emptySlots < 0 || mergeSpace < 0) return false;
        int fullCapacity = emptySlots * 64 + mergeSpace;
        int reserve = mossRemaining <= fullCapacity ? 0 : 1;
        int safeCapacity = Math.max(0, emptySlots - reserve) * 64 + mergeSpace;
        return stackCount <= safeCapacity;
    }
}
