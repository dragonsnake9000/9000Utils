package dev.dragonsnake9000.utils;

/** Chooses one whole-stack swap, never cursor pickup/merge clicks. Slot 40 is the offhand. */
final class MossHotbarPlan {
    record Swap(int source,int destination) { }
    static Swap choose(int[] moss,boolean[] empty,int selected,int threshold) {
        int destination=-1;
        for (int i=0;i<9;i++) if (moss[i]>0) { destination=i; break; }
        boolean missing=destination<0;
        if (!missing && moss[destination]>threshold) return null;
        int source=-1;
        for (int i=9;i<=40;i++) {
            if (i>=36 && i!=40) continue;
            // Low partial stacks must not bounce back and forth on every consumed block.
            if (moss[i]<=0 || (!missing && moss[i]<=threshold)) continue;
            if (source<0 || moss[i]>moss[source]) source=i;
        }
        if (source<0) return null;
        if (missing) {
            destination=selected;
            for (int i=0;i<9;i++) if (empty[i]) { destination=i; break; }
        }
        return destination < 0 ? null : new Swap(source,destination);
    }
}
