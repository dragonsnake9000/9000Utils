package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LightCoverageTest {
    record Cell(int x, int z) {
        List<Cell> neighbors() { return List.of(new Cell(x+1,z),new Cell(x-1,z),new Cell(x,z+1),new Cell(x,z-1)); }
    }
    @Test void jackLanternDiamondCovers421NotACircle() {
        var light = LightCoverage.propagate(new Cell(0,0),15,1,Cell::neighbors,p -> true);
        assertEquals(421, light.size());
        assertEquals(1, light.get(new Cell(7,7)));
        assertFalse(light.containsKey(new Cell(8,7)));
        assertEquals(LightCoverage.flatArea(15,1), light.size());
    }
    @Test void wallBlocksLightButDetourCostsDistance() {
        var light = LightCoverage.propagate(new Cell(0,0),6,1,Cell::neighbors,p -> !(p.x==1 && Math.abs(p.z)<=1));
        assertFalse(light.containsKey(new Cell(2,0))); // Direct distance 2; around wall requires 6.
        assertEquals(2, light.get(new Cell(2,2)));
    }
    @Test void weakerSourcesNeedCloserSpacing() {
        assertEquals(365, LightCoverage.flatArea(14,1));
        assertEquals(85, LightCoverage.flatArea(7,1));
        assertEquals(0, LightCoverage.lightAt(15,15,0,0));
        assertEquals(1, LightCoverage.lightAt(15,7,0,7));
    }
    @Test void elevatedLightsLoseCoverageAtRoofLevel() {
        assertEquals(0, LightCoverage.lightAt(15,14,1,0));
        assertEquals(1, LightCoverage.lightAt(15,13,1,0));
    }
    @Test void requestedThresholdChangesCoverage() {
        var light = LightCoverage.propagate(new Cell(0,0),15,8,Cell::neighbors,p -> true);
        assertEquals(113, light.size());
        assertTrue(light.values().stream().allMatch(v -> v>=8));
        assertTrue(LightCoverage.propagate(0,7,8,p -> List.of(p+1),p -> true).isEmpty());
    }
}
