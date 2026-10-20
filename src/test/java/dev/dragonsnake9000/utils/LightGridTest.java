package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LightGridTest {
    @Test void defaultLayoutHasExpectedFullProjectCountAndStaysInsideBounds() {
        LightGrid grid=new LightGrid(-5000,5000,-5000,5000,13);
        var sites=grid.sites(-5000,5000,-5000,5000);
        assertEquals(297221,sites.size());
        assertTrue(sites.stream().allMatch(p -> grid.inside(p.x(),p.z())));
        assertTrue(sites.contains(new LightGrid.Site(0,0)));
        assertTrue(sites.contains(new LightGrid.Site(13,13)));
        assertFalse(sites.contains(new LightGrid.Site(13,0)));
    }
    @Test void everyCellInTwoInteriorPeriodsHasLightTwoOrBetter() {
        LightGrid grid=new LightGrid(-5000,5000,-5000,5000,13);
        var sites=grid.sites(-60,60,-60,60);
        for (int x=-26;x<=26;x++) for (int z=-26;z<=26;z++)
            assertTrue(nearest(sites,x,z)<=13,"Uncovered interior cell "+x+","+z);
    }
    @Test void projectionCoversEveryEdgeCellAndCornersWithoutOutsideLights() {
        LightGrid grid=new LightGrid(-5000,5000,-5000,5000,13);
        for (int n=-5000;n<=5000;n++) {
            check(grid,n,-5000); check(grid,n,5000); check(grid,-5000,n); check(grid,5000,n);
        }
    }
    @Test void localWindowsAgreeWithWholePlanIncludingNegativeCoordinatesAndEdges() {
        LightGrid grid=new LightGrid(-53,47,-39,62,13);
        var all=grid.sites(-53,47,-39,62);
        for (int x=-60;x<60;x+=7) for (int z=-45;z<70;z+=9) {
            int lowX=x,highX=x+8,lowZ=z,highZ=z+8;
            var expected=all.stream().filter(p -> p.x()>=lowX && p.x()<=highX && p.z()>=lowZ && p.z()<=highZ)
                .collect(java.util.stream.Collectors.toSet());
            assertEquals(expected,grid.sites(lowX,highX,lowZ,highZ));
        }
    }
    @Test void narrowRegionsAndReversedBoundsStillHaveCompleteCoverage() {
        for (int radius=1;radius<=14;radius++) {
            LightGrid grid=new LightGrid(4,-3,8,-7,radius);
            var sites=grid.sites(-3,4,-7,8);
            for (int x=-3;x<=4;x++) for (int z=-7;z<=8;z++) assertTrue(nearest(sites,x,z)<=radius);
        }
    }
    @Test void zeroRadiusMeansEveryCellAndVisualLimitIsBounded() {
        LightGrid grid=new LightGrid(-2,2,-2,2,0);
        assertEquals(25,grid.sites(-2,2,-2,2).size());
        assertEquals(3,grid.sites(-2,2,-2,2,3).size());
        assertTrue(grid.sites(10,20,10,20).isEmpty());
    }
    @Test void overlapCalculationUsesLightThresholdAndExtraMargin() {
        assertEquals(13,LightGrid.radius(15,1,1));
        assertEquals(14,LightGrid.radius(15,1,0));
        assertEquals(12,LightGrid.radius(15,2,1));
        assertEquals(0,LightGrid.radius(15,15,1));
    }
    private static void check(LightGrid grid,int x,int z) {
        assertTrue(nearest(grid.sites(x-13,x+13,z-13,z+13),x,z)<=13,"Uncovered edge "+x+","+z);
    }
    private static int nearest(Set<LightGrid.Site> sites,int x,int z) {
        return sites.stream().mapToInt(p -> Math.abs(p.x()-x)+Math.abs(p.z()-z)).min().orElse(Integer.MAX_VALUE);
    }
}
