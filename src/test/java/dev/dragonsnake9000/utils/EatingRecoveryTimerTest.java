package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EatingRecoveryTimerTest {
    @Test void successfulConsumptionPreventsRecovery() {
        var timer = new EatingRecoveryTimer();
        for (int tick = 0; tick < 300; tick++) assertFalse(timer.update(true, 64 - tick / 32));
    }
    @Test void stalledEatingGetsOnlyThreeSpacedRetries() {
        var timer = new EatingRecoveryTimer(); int retries = 0;
        for (int tick = 0; tick < 1000; tick++) if (timer.update(true, 64)) retries++;
        assertEquals(3, retries);
        timer.update(false, 64);
        for (int tick = 0; tick < 99; tick++) assertFalse(timer.update(true, 64));
        assertTrue(timer.update(true, 64));
    }
}
