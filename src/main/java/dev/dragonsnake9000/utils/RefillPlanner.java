package dev.dragonsnake9000.utils;

/** Conservative quick-move budget; retain an empty slot unless all remaining moss fits. */
final class RefillPlanner {
    static boolean canQuickMoveMoss(int stackCount,int emptySlots,int mainEmpty,int mergeSpace) {
        // A transfer needing a new slot must not consume the last main-inventory light slot.
        return mainEmpty>=1 && canTakeMossStack(stackCount,emptySlots,mergeSpace)
            && (stackCount<=mergeSpace || mainEmpty>1);
    }
    static boolean canTakeMossStack(int stackCount,int emptySlots,int mergeSpace) {
        return stackCount>0 && emptySlots>=0 && mergeSpace>=0
            && stackCount<=Math.max(0,emptySlots-2)*64+mergeSpace;
    }
    static boolean canTakeStack(int stackCount, int mossRemaining, int emptySlots, int mergeSpace) {
        if (stackCount <= 0 || mossRemaining < stackCount || emptySlots < 0 || mergeSpace < 0) return false;
        int fullCapacity = emptySlots * 64 + mergeSpace;
        int reserve = mossRemaining <= fullCapacity ? 0 : 1;
        int safeCapacity = Math.max(0, emptySlots - reserve) * 64 + mergeSpace;
        return stackCount <= safeCapacity;
    }
}
