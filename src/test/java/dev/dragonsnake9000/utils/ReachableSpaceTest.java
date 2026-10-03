package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.dragonsnake9000.utils.ReachableSpace.*;

class ReachableSpaceTest {
    @Test void bodyOverlappingKnownSolidIsTrappedRatherThanUnknown() {
        Pos origin = new Pos(0, 0, 0);
        assertEquals(0, count(origin, 20, p -> p.equals(origin) ? Cell.Solid : Cell.Open));
        assertEquals(0, count(origin, 20, p -> p.equals(origin.add(0, 1, 0)) ? Cell.Solid : Cell.Open));
        assertEquals(-1, count(origin, 20, p -> Cell.Unknown));
    }
    @Test void fullySealedAirCellHasOnePosition() {
        Pos origin = new Pos(0, 0, 0);
        assertEquals(1, count(origin, 20, p -> p.equals(origin) || p.equals(origin.add(0, 1, 0))
            ? Cell.Open : Cell.Solid));
    }
    @Test void sealedPlayerCanEscapeIntoSmallPocketButOrdinaryPocketStillNeedsLargerExit() {
        assertTrue(opensExit(0, 3, 20));
        assertTrue(opensExit(1, 3, 20));
        assertFalse(opensExit(0, 1, 20));
        assertFalse(opensExit(1, -1, 20));
        assertFalse(opensExit(9, 12, 20));
        assertTrue(opensExit(9, 21, 20));
    }
    private final Pos start = new Pos(0, 0, 0);
    private Terrain room(int radius) {
        return p -> p.y() < 0 || Math.abs(p.x()) > radius || Math.abs(p.z()) > radius
            ? Cell.Solid : Cell.Open;
    }
    @Test void countsCombinedAreaRatherThanSingleDirectionOrRepeatedTravel() {
        assertEquals(9, count(start, 20, room(1)));
        assertEquals(9, count(new Pos(1, 0, 1), 20, room(1)));
        assertTrue(count(start, 20, room(2)) > 20);
    }
    @Test void openTerrainCannotTriggerOnPathfindingStall() {
        assertTrue(count(start, 20, p -> p.y() < 0 ? Cell.Solid : Cell.Open) > 20);
    }
    @Test void countsSteppedExitAndGapJump() {
        // The only initial support is one block; all additional space requires the tested move.
        Pos support = start.add(0, -1, 0);
        assertTrue(count(start, 20, p -> p.equals(support) || (p.x() >= 1 && p.y() <= 0)
            ? Cell.Solid : Cell.Open) > 20);
        assertTrue(count(start, 20, p -> p.equals(support) || (p.x() >= 3 && p.y() < 0)
            ? Cell.Solid : Cell.Open) > 20);
        assertEquals(1, count(start, 20, p -> p.equals(support) || (p.x() >= 5 && p.y() < 0)
            ? Cell.Solid : Cell.Open));
    }
    @Test void uncertainTerrainDoesNotProveTrap() {
        assertEquals(-1, count(start, 20, p -> p.equals(new Pos(1, 0, 0)) ? Cell.Unknown : room(1).cell(p)));
    }
    @Test void removingWallOpensPocket() {
        Terrain sealed = p -> p.y() < 0 || (Math.abs(p.x()) == 2 || Math.abs(p.z()) == 2) ? Cell.Solid : Cell.Open;
        assertEquals(9, count(start, 20, sealed));
        assertTrue(count(start, 20, p -> p.x() == 2 && p.z() == 0 && p.y() >= 0 && p.y() <= 1
            ? Cell.Open : sealed.cell(p)) > 20);
    }
}
