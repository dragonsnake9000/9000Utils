package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SectorGridTest {
    @Test void mapOrientationAndBoundaries() {
        assertEquals("A1",SectorGrid.name(SectorGrid.at(-5000,-5000)));
        assertEquals("J10",SectorGrid.name(SectorGrid.at(4999.99,4999.99)));
        assertEquals("F6",SectorGrid.name(SectorGrid.at(0,0)));
        assertEquals("B1",SectorGrid.name(SectorGrid.at(-4000,-4500)));
        assertEquals(-1,SectorGrid.at(5000,0));assertEquals(-1,SectorGrid.at(-5000.01,0));
    }
    @Test void everySectorHasExactPaddingAndDistinctCenter() {
        for(int i=0;i<100;i++) {
            var b=SectorGrid.bounds(i,60,320);
            assertEquals(1010,b.maxX()-b.minX()+1);assertEquals(1010,b.maxZ()-b.minZ()+1);
            assertEquals(SectorGrid.minX(i)-5,b.minX());assertEquals(SectorGrid.minZ(i)+1004,b.maxZ());
            assertEquals(60,b.minY());assertEquals(320,b.maxY());
            assertEquals(i,SectorGrid.at(SectorGrid.minX(i)+500,SectorGrid.minZ(i)+500));
        }
    }
    @Test void planeIsPermanently600() {
        for(double y:new double[]{-64,0,315,330,400,1000})assertEquals(600,SectorGrid.plane(y));
    }
}
