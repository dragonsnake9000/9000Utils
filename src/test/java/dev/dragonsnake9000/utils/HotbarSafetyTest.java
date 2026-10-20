package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HotbarSafetyTest {
    @Test void selectedSwordIsNeverBorrowed() {
        boolean[] protectedSlots=new boolean[9];protectedSlots[2]=true;
        assertEquals(0,HotbarSafety.choose(new boolean[9],protectedSlots,2));
    }
    @Test void emptySlotWinsBeforeNonSwordSelectedSlot() {
        boolean[] empty=new boolean[9];empty[8]=true;
        assertEquals(8,HotbarSafety.choose(empty,new boolean[9],3));
    }
    @Test void allSwordHotbarCannotBeBorrowedOrUsedForReplenishment() {
        boolean[] protectedSlots=new boolean[9];java.util.Arrays.fill(protectedSlots,true);
        int slot=HotbarSafety.choose(new boolean[9],protectedSlots,0);assertEquals(-1,slot);
        int[] moss=new int[41];moss[9]=64;
        assertNull(MossHotbarPlan.choose(moss,new boolean[9],slot,16));
    }
}
