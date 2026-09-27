package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationBudgetTest {
    @Test void backgroundScansNeverResubmitAnUnchangedGoal() {
        NavigationBudget<String> budget = new NavigationBudget<>();
        assertTrue(budget.canDispatch(1, "A", 10));
        budget.dispatched(1, "A");
        for (int tick = 2; tick < 1000; tick++) assertFalse(budget.canDispatch(tick, "A", 10));
    }
    @Test void cancellationDoesNotBypassTheRateLimit() {
        NavigationBudget<String> budget = new NavigationBudget<>();
        budget.dispatched(100, "A");
        budget.clear();
        assertFalse(budget.canDispatch(101, "B", 10));
        assertTrue(budget.canDispatch(110, "B", 10));
    }
    @Test void limitsRapidlyCoveredTargetsEvenWhenConfiguredToZero() {
        NavigationBudget<String> budget = new NavigationBudget<>();
        budget.dispatched(1, "A");
        budget.clear();
        assertFalse(budget.canDispatch(1, "B", 0));
        assertTrue(budget.canDispatch(2, "B", 0));
        assertFalse(budget.canDispatch(2, null, 0));
    }
    @Test void worldChangeResetsTheOldTickClock() {
        NavigationBudget<String> budget = new NavigationBudget<>();
        budget.dispatched(9999, "A");
        budget.reset();
        assertTrue(budget.canDispatch(1, "A", 10));
    }
}
