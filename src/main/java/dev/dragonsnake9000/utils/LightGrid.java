package dev.dragonsnake9000.utils;

import java.util.LinkedHashSet;
import java.util.Set;

/** Diamond lattice matched to Manhattan light falloff. Outside centers project onto the roof edge. */
final class LightGrid {
    record Site(int x, int z) { }
    final int minX, maxX, minZ, maxZ, radius;
    LightGrid(int fromX, int toX, int fromZ, int toZ, int radius) {
        minX=Math.min(fromX,toX); maxX=Math.max(fromX,toX);
        minZ=Math.min(fromZ,toZ); maxZ=Math.max(fromZ,toZ);
        this.radius=Math.max(0,radius);
    }
    static int radius(int emission,int minimum,int overlap) { return Math.max(0,emission-minimum-overlap); }
    boolean inside(int x,int z) { return x>=minX && x<=maxX && z>=minZ && z<=maxZ; }
    /** Origin stays at world (0,0), including at negative coordinates and across restarts. */
    Set<Site> sites(int fromX,int toX,int fromZ,int toZ) {
        return sites(fromX,toX,fromZ,toZ,Integer.MAX_VALUE);
    }
    Set<Site> sites(int fromX,int toX,int fromZ,int toZ,int limit) {
        int lowX=Math.max(minX,fromX), highX=Math.min(maxX,toX);
        int lowZ=Math.max(minZ,fromZ), highZ=Math.min(maxZ,toZ);
        Set<Site> result=new LinkedHashSet<>();
        if (lowX>highX || lowZ>highZ) return result;
        int step=Math.max(1,radius), period=radius==0 ? 1 : step*2;
        int firstRow=Math.floorDiv(lowZ-step,step), lastRow=Math.floorDiv(highZ+step,step);
        for (int row=firstRow;row<=lastRow;row++) {
            int rawZ=row*step;
            if (rawZ<minZ-step || rawZ>maxZ+step) continue;
            int z=Math.clamp(rawZ,minZ,maxZ);
            if (z<lowZ || z>highZ) continue;
            int offset=radius==0 ? 0 : Math.floorMod(row,2)*step;
            int firstColumn=Math.floorDiv(lowX-step-offset,period);
            int lastColumn=Math.floorDiv(highX+step-offset,period);
            for (int column=firstColumn;column<=lastColumn;column++) {
                int rawX=offset+column*period;
                if (rawX<minX-step || rawX>maxX+step) continue;
                int x=Math.clamp(rawX,minX,maxX);
                if (x>=lowX && x<=highX) {
                    result.add(new Site(x,z));
                    if (result.size()>=limit) return result;
                }
            }
        }
        return result;
    }
}
