package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceSweepTest {
    @Test void descendingBandStaysStableAcrossTargetSelections() {
        SurfaceSweep sweep = new SurfaceSweep();
        sweep.begin(100, -1, 3, 0, 0, 10, 0);
        sweep.selected(0, 10);
        assertTrue(sweep.accepts(100)); assertTrue(sweep.accepts(98));
        assertFalse(sweep.accepts(101)); assertFalse(sweep.accepts(97));
        assertTrue(sweep.ahead(97)); assertFalse(sweep.ahead(101));
    }
    @Test void ascendingBandMovesInOppositeHeightDirection() {
        SurfaceSweep sweep = new SurfaceSweep();
        sweep.begin(57, 1, 3, 0, 0, 10, 0);
        assertTrue(sweep.accepts(57)); assertTrue(sweep.accepts(59));
        assertFalse(sweep.accepts(56)); assertFalse(sweep.accepts(60));
        assertTrue(sweep.ahead(60)); assertFalse(sweep.ahead(56));
    }
    @Test void nearbyClockwiseContinuationBeatsReversingTowardTheLastPatch() {
        SurfaceSweep sweep = new SurfaceSweep();
        sweep.begin(100, -1, 3, 0, 0, 10, 0);
        assertTrue(sweep.rank(9, 1, 2) < sweep.rank(9, -1, 2));
        sweep.selected(0, 10);
        assertTrue(sweep.rank(-1, 9, 2) < sweep.rank(1, 9, 2));
    }
    @Test void angleWrapCrossesTheSeamWithoutReversing() {
        assertEquals(.2, SurfaceSweep.forwardAngle(Math.PI - .1, -Math.PI + .1), 1e-9);
        assertEquals(Math.PI * 2 - .2, SurfaceSweep.forwardAngle(-Math.PI + .1, Math.PI - .1), 1e-9);
    }
    @Test void resetAllowsSelectingANewBand() {
        SurfaceSweep sweep = new SurfaceSweep();
        sweep.begin(100, -1, 1, 0, 0, 0, 0);
        assertFalse(sweep.accepts(99));
        sweep.reset();
        assertFalse(sweep.active()); assertTrue(sweep.accepts(99));
        assertEquals(7, sweep.rank(10, 10, 7));
    }
}
