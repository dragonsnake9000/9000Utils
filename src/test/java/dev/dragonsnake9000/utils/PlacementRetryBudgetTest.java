package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacementRetryBudgetTest {
    @Test void repeatedSelfCollisionAbandonsOnceAfterOneSecond() {
        var budget = new PlacementRetryBudget();
        for (int tick = 0; tick < 20; tick += 5) { budget.record(1, tick, true); assertFalse(budget.consume()); }
        budget.record(1, 20, true);
        assertTrue(budget.consume());
        assertFalse(budget.consume());
    }
    @Test void validPillarPlacementClearsPreviousCollisions() {
        var budget = new PlacementRetryBudget();
        for (int tick = 0; tick <= 20; tick += 5) budget.record(1, tick, true);
        budget.record(1, 21, false);
        assertFalse(budget.consume());
    }
    @Test void differentCellsAndSameTickBurstsDoNotTriggerStall() {
        var budget = new PlacementRetryBudget();
        for (int i = 0; i < 100; i++) budget.record(1, 0, true);
        budget.record(1, 20, true);
        assertFalse(budget.consume());
        for (int i = 0; i < 100; i++) { budget.record(i, 30 + i, true); assertFalse(budget.consume()); }
    }
}
