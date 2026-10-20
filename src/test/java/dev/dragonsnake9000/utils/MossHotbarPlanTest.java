package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MossHotbarPlanTest {
    @Test void replacesBeforeEmptyAndChoosesLargestReserve() {
        int[] moss=new int[41]; moss[2]=16; moss[10]=32; moss[18]=64;
        assertEquals(new MossHotbarPlan.Swap(18,2),MossHotbarPlan.choose(moss,new boolean[9],0,16));
    }
    @Test void leavesHealthyStackAlone() {
        int[] moss=new int[41]; moss[0]=17; moss[9]=64;
        assertNull(MossHotbarPlan.choose(moss,new boolean[9],0,16));
    }
    @Test void doesNotShuffleLowPartialStacksBackAndForth() {
        int[] moss=new int[41]; moss[0]=8; moss[9]=16; moss[40]=12;
        assertNull(MossHotbarPlan.choose(moss,new boolean[9],0,16));
    }
    @Test void emptyHotbarAcceptsSmallReserveAndPrefersEmptySlot() {
        int[] moss=new int[41]; moss[9]=3; boolean[] empty=new boolean[9]; empty[6]=true;
        assertEquals(new MossHotbarPlan.Swap(9,6),MossHotbarPlan.choose(moss,empty,0,16));
    }
    @Test void fullHotbarUsesSelectedSlotWithoutDiscardingItems() {
        int[] moss=new int[41]; moss[12]=64;
        assertEquals(new MossHotbarPlan.Swap(12,4),MossHotbarPlan.choose(moss,new boolean[9],4,16));
    }
    @Test void offhandCanSupplyMossButArmorSlotsCannot() {
        int[] moss=new int[41]; moss[36]=64; moss[40]=32;
        assertEquals(new MossHotbarPlan.Swap(40,0),MossHotbarPlan.choose(moss,new boolean[9],0,16));
        moss[40]=0; assertNull(MossHotbarPlan.choose(moss,new boolean[9],0,16));
    }
    @Test void noSupplyMeansNoClick() {
        assertNull(MossHotbarPlan.choose(new int[41],new boolean[9],0,16));
    }
    @Test void preservesOtherHotbarMossStacksRatherThanSwappingThemInCircles() {
        int[] moss=new int[41]; moss[0]=3; moss[4]=64;
        assertNull(MossHotbarPlan.choose(moss,new boolean[9],0,16));
    }
}
