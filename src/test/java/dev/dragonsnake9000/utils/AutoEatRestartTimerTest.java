package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutoEatRestartTimerTest {
    @Test void firesAtTwentyTicksOnlyOnceDuringContinuousEating() {
        var timer = new AutoEatRestartTimer();
        for (int i = 0; i < 19; i++) assertFalse(timer.update(true));
        assertTrue(timer.update(true));
        for (int i = 0; i < 200; i++) assertFalse(timer.update(true));
    }
    @Test void restartGapDoesNotRearmButSeparateMealDoes() {
        var timer = new AutoEatRestartTimer();
        for (int i = 0; i < 20; i++) timer.update(true);
        for (int i = 0; i < 5; i++) assertFalse(timer.update(false));
        for (int i = 0; i < 40; i++) assertFalse(timer.update(true));
        for (int i = 0; i < 40; i++) assertFalse(timer.update(false));
        for (int i = 0; i < 19; i++) assertFalse(timer.update(true));
        assertTrue(timer.update(true));
    }
    @Test void doesNotRestartAfterMealEndsAndResetClearsPendingRestart() {
        var timer = new AutoEatRestartTimer();
        for (int i = 0; i < 10; i++) assertFalse(timer.update(true));
        for (int i = 0; i < 40; i++) assertFalse(timer.update(false));
        timer.reset();
        for (int i = 0; i < 19; i++) assertFalse(timer.update(true));
        assertTrue(timer.update(true));
    }
}
