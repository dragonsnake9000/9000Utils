package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefillPlannerTest {
    @Test void retainsLastSlotWhenBoxWillStillContainMoss() {
        assertFalse(RefillPlanner.canTakeStack(64, 128, 1, 0));
        assertTrue(RefillPlanner.canTakeStack(64, 192, 2, 0));
    }
    @Test void canUseLastSlotWhenEntireRemainingContentsFit() {
        assertTrue(RefillPlanner.canTakeStack(64, 64, 1, 0));
        assertTrue(RefillPlanner.canTakeStack(32, 96, 1, 32));
    }
    @Test void accountsForPartialStacksWithoutSpendingReservedSlot() {
        assertTrue(RefillPlanner.canTakeStack(16, 200, 1, 16));
        assertFalse(RefillPlanner.canTakeStack(17, 200, 1, 16));
        assertFalse(RefillPlanner.canTakeStack(64, 64, 0, 0));
    }
    @Test void rejectsInvalidInput() {
        assertFalse(RefillPlanner.canTakeStack(0, 0, 1, 0));
        assertFalse(RefillPlanner.canTakeStack(64, 32, 1, 0));
        assertFalse(RefillPlanner.canTakeStack(64, 64, -1, 0));
    }

    @Test void completeRefillSequencesNeverConsumeRecoverySlotForAnUnfinishedBox() {
        for (int startingEmpty = 1; startingEmpty <= 10; startingEmpty++) {
            for (int startingMerge = 0; startingMerge <= 64; startingMerge += 8) {
                for (int contents = 1; contents <= 27 * 64; contents++) {
                    int empty = startingEmpty, merge = startingMerge, remaining = contents;
                    while (remaining > 0) {
                        int stack = Math.min(64, remaining);
                        if (!RefillPlanner.canTakeStack(stack, remaining, empty, merge)) break;
                        int intoExisting = Math.min(stack, merge);
                        merge -= intoExisting;
                        int intoNew = stack - intoExisting;
                        if (intoNew > 0) { empty--; merge += 64 - intoNew; }
                        assertTrue(empty >= 0);
                        remaining -= stack;
                    }
                    assertTrue(remaining == 0 || empty >= 1,
                        "A partially full shulker must have a reserved inventory slot");
                }
            }
        }
    }
}
