package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaceThreatTrackerTest {
    private final MaceThreatTracker.Rules rules = new MaceThreatTracker.Rules(.8, 5, 2.5, 8, true, 2);
    private boolean step(MaceThreatTracker tracker, int tick, double x, double y, boolean ground,
                         boolean elytra, boolean armor, boolean gliding, boolean mace) {
        return tracker.update(new MaceThreatTracker.Sample(tick, x, y, 0), ground, elytra, armor, gliding,
            mace, 0, 0, 0, 0, 0, 0, rules);
    }
    @Test void approachingMaceDiveRequiresConsecutiveEvidence() {
        var tracker = new MaceThreatTracker();
        for (int t = 0; t <= 7; t++) assertFalse(step(tracker, t, 12 - t, 30 - 2 * t, false, t < 4, t >= 4, t < 4, t >= 4));
        assertTrue(step(tracker, 8, 4, 14, false, false, true, false, true));
        assertTrue(tracker.reasons().contains("Mace held in main hand"));
        assertTrue(tracker.reasons().contains("Confirmed for 2 consecutive ticks"));
        assertTrue(tracker.reasons().contains("chestplate swap during rapid airborne descent"));
        assertTrue(tracker.reasons().contains("Recent elytra flight observed"));
    }
    @Test void gearSwapWithoutMaceNeverTriggers() {
        var tracker = new MaceThreatTracker();
        for (int t = 0; t < 15; t++) assertFalse(step(tracker, t, 12 - t, 30 - 2 * t, false, t < 4, t >= 4, t < 4, false));
    }
    @Test void ordinaryCliffFallWithoutFlightEvidenceDoesNotTrigger() {
        var tracker = new MaceThreatTracker();
        for (int t = 0; t < 15; t++) assertFalse(step(tracker, t, 0, 30 - 2 * t, false, false, true, false, true));
    }
    @Test void swapBeforeRapidDescentIsNotFlightEvidence() {
        var tracker = new MaceThreatTracker();
        step(tracker, 0, 0, 30, false, true, false, false, true);
        step(tracker, 1, 0, 30, false, false, true, false, true);
        for (int t = 2; t < 15; t++) assertFalse(step(tracker, t, 0, 32 - 2 * t, false, false, true, false, true));
    }
    @Test void airborneRapidSwapCanSupportDetectionWithoutObservedGliding() {
        var tracker = new MaceThreatTracker();
        boolean detected = false;
        for (int t = 0; t <= 8; t++) detected |= step(tracker, t, 12 - t, 30 - 2 * t, false, t < 4, t >= 4, false, true);
        assertTrue(detected);
    }
    @Test void flyingPastAndAscendingAreNotThreats() {
        var passing = new MaceThreatTracker();
        var ascending = new MaceThreatTracker();
        for (int t = 0; t < 15; t++) {
            assertFalse(step(passing, t, 30, 30 - 2 * t, false, true, false, true, true));
            assertFalse(step(ascending, t, 0, 4 + t, false, true, false, true, true));
        }
    }
    @Test void groundedArmorRefreshAndTeleportDoNotTrigger() {
        var tracker = new MaceThreatTracker();
        for (int t = 0; t < 20; t++) assertFalse(step(tracker, t, 0, 10, true, t % 2 == 0, t % 2 != 0, false, true));
        assertFalse(step(tracker, 20, 0, 100, false, true, false, true, true));
        assertFalse(step(tracker, 21, 0, 4, false, false, true, false, true));
    }
}
