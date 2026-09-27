package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionBoundsTest {
    @Test void returnRangeUsesNearestRegionPointIncludingHeight() {
        RegionBounds box = new RegionBounds(-5, 57, -5, 5, 254, 5);
        assertEquals(25, box.distanceSquared(8, 53, 0));
        assertEquals(0, box.distanceSquared(0, 57, 0));
        assertEquals(-5, box.clampX(-100));
        assertEquals(254, box.clampY(300));
    }
    @Test void temporaryEntryCorridorDoesNotRemoveTheRegionRestriction() {
        try {
            RegionConstraint.bounds = new RegionBounds(-5, 57, -5, 5, 254, 5);
            RegionConstraint.entryCorridor = new RegionBounds(5, 57, -2, 12, 65, 2);
            assertTrue(RegionConstraint.allows(10, 60, 0));
            assertFalse(RegionConstraint.allows(10, 80, 0));
            RegionConstraint.entryCorridor = null;
            assertFalse(RegionConstraint.allows(10, 60, 0));
        } finally { RegionConstraint.bounds = null; RegionConstraint.entryCorridor = null; }
    }
    @Test void reversedCornersAndInclusiveEdges() {
        RegionBounds box = RegionBounds.normalized(5000, 254, 5000, -5000, 57, -5000);
        assertTrue(box.contains(-5000, 57, -5000));
        assertTrue(box.contains(5000, 254, 5000));
        assertFalse(box.contains(-5001, 57, 0));
        assertFalse(box.contains(0, 56, 0));
        assertFalse(box.contains(0, 255, 0));
        assertFalse(box.contains(0, 57, 5001));
    }
    @Test void oneBlockRegionAndWorldScaleCoordinates() {
        assertTrue(RegionBounds.normalized(1, 2, 3, 1, 2, 3).contains(1, 2, 3));
        assertFalse(RegionBounds.normalized(1, 2, 3, 1, 2, 3).contains(1, 2, 4));
        RegionBounds box = RegionBounds.normalized(-30000000, -64, -30000000, 30000000, 320, 30000000);
        assertTrue(box.contains(30000000, 320, -30000000));
        assertFalse(box.contains(30000001, 320, 0));
    }
    @Test void liveRestrictionCanBeReplacedAndDisabled() {
        try {
            RegionConstraint.bounds = new RegionBounds(-5, 57, -5, 5, 254, 5);
            assertFalse(RegionConstraint.allows(6, 100, 0));
            RegionConstraint.bounds = new RegionBounds(-10, 57, -10, 10, 254, 10);
            assertTrue(RegionConstraint.allows(6, 100, 0));
            RegionConstraint.bounds = null;
            assertTrue(RegionConstraint.allows(6, 0, 0));
        } finally { RegionConstraint.bounds = null; }
    }
}
