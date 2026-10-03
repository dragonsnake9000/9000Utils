package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScaffoldRetreatBudgetTest {
    @Test void repeatedApproachesShareOneBoundedRetreatBudget() {
        var budget = new ScaffoldRetreatBudget();
        for (int tick = 0; tick < 40; tick++) assertTrue(budget.allow(123, tick * 5));
        assertFalse(budget.allow(123, 300));
        assertFalse(budget.allow(123, 301));
        assertTrue(budget.allow(456, 302));
    }
    @Test void duplicateUpdatesDoNotSpendExtraTicksAndResetClearsBudget() {
        var budget = new ScaffoldRetreatBudget();
        for (int i = 0; i < 100; i++) assertTrue(budget.allow(123, 1));
        for (int tick = 2; tick <= 40; tick++) assertTrue(budget.allow(123, tick));
        assertFalse(budget.allow(123, 41));
        budget.reset();
        assertTrue(budget.allow(123, 42));
    }
}
