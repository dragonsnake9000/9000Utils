package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TargetRulesTest {
    @Test void acceptsExactlyTenAirBlocks() {
        assertTrue(TargetRules.hasClearance(64, 320, 10, y -> y >= 65 && y <= 74));
    }
    @Test void rejectsEveryPossibleObstructionIncludingTheTenthBlock() {
        for (int offset = 1; offset <= 10; offset++) {
            int blockedY = 64 + offset;
            assertFalse(TargetRules.hasClearance(64, 320, 10, y -> y != blockedY));
        }
    }
    @Test void doesNotCountOutOfWorldAir() {
        assertFalse(TargetRules.hasClearance(310, 320, 10, y -> true));
        assertTrue(TargetRules.hasClearance(309, 320, 10, y -> true));
    }
    @Test void supportsNegativeHeightAndConfigurableClearance() {
        assertTrue(TargetRules.hasClearance(-64, 320, 10, y -> true));
        assertTrue(TargetRules.hasClearance(64, 320, 9, y -> true));
        assertFalse(TargetRules.hasClearance(64, 320, 12, y -> y != 76));
    }
    @Test void zeroClearanceStillRequiresAnExposedTop() {
        assertTrue(TargetRules.hasClearance(64, 320, 0, y -> y == 65));
        assertFalse(TargetRules.hasClearance(64, 320, 0, y -> false));
        assertFalse(TargetRules.hasClearance(319, 320, 0, y -> true));
        assertFalse(TargetRules.hasClearance(64, 320, -1, y -> true));
    }
    @Test void countsAll26DirectionsButNotTheCenter() {
        assertEquals(26, TargetRules.countNeighbors((x, y, z) -> true));
        assertEquals(0, TargetRules.countNeighbors((x, y, z) -> x == 0 && y == 0 && z == 0));
        assertEquals(2, TargetRules.countNeighbors((x, y, z) -> x == 0 && z == 0 && y != 0));
        assertEquals(8, TargetRules.countNeighbors((x, y, z) -> x != 0 && y != 0 && z != 0));
    }
    @Test void densityBiasFavorsNearbyClustersWithoutIgnoringDistance() {
        assertTrue(TargetRules.score(5, 8, 1) < TargetRules.score(4, 0, 1));
        assertTrue(TargetRules.score(30, 26, 1) > TargetRules.score(4, 0, 1));
        assertEquals(5, TargetRules.score(5, 26, 0));
    }
    @Test void expandsOnlyAfterTheConfiguredEmptyPeriod() {
        assertFalse(TargetRules.fallbackReady(1000, -1, 100));
        assertFalse(TargetRules.fallbackReady(199, 100, 100));
        assertTrue(TargetRules.fallbackReady(200, 100, 100));
        assertTrue(TargetRules.fallbackReady(100, 100, 0));
    }
}
