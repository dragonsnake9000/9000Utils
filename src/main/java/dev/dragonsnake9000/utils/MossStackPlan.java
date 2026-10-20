package dev.dragonsnake9000.utils;

/** One merge at a time in main inventory; the caller groups stacks by identical item components. */
final class MossStackPlan {
    record Merge(int source,int destination) { }
    static Merge choose(int[] counts,int capacity) {
        int total=0,target=-1,source=-1;
        for (int i=9;i<36;i++) if (counts[i]>0 && counts[i]<capacity) {
            total+=counts[i];
            if (target<0 || counts[i]>counts[target]) target=i;
        }
        if (total<capacity) return null;
        for (int i=9;i<36;i++) if (i!=target && counts[i]>0 && counts[i]<capacity
            && (source<0 || counts[i]>counts[source])) source=i;
        return source<0 ? null : new Merge(source,target);
    }
}
