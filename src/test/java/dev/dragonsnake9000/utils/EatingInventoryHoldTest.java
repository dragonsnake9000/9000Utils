package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EatingInventoryHoldTest {
    @Test void fixedDurationDoesNotDependOnEating() {
        var hold = new EatingInventoryHold();
        hold.start(20);
        for (int i = 0; i < 19; i++) assertFalse(hold.shouldClose(false, false));
        assertTrue(hold.shouldClose(false, true));
    }
    @Test void entireMealIgnoresDurationAndToleratesInterBiteGap() {
        var hold = new EatingInventoryHold();
        hold.start(2);
        for (int i = 0; i < 100; i++) assertFalse(hold.shouldClose(true, true));
        assertFalse(hold.shouldClose(true, false));
        assertFalse(hold.shouldClose(true, true));
        assertFalse(hold.shouldClose(true, false));
        assertTrue(hold.shouldClose(true, false));
    }
    @Test void newRecoveryResetsQuietTime() {
        var hold = new EatingInventoryHold();
        hold.start(2);
        hold.shouldClose(true, false);
        hold.start(2);
        assertFalse(hold.shouldClose(true, false));
        assertTrue(hold.shouldClose(true, false));
    }
}
