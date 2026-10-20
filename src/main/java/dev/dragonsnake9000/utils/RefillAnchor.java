package dev.dragonsnake9000.utils;

/** Refill return targets the saved standing block; ignore tiny nudges within that block. */
record RefillAnchor(double x,double y,double z) {
    int blockX() { return (int)Math.floor(x); }
    int blockY() { return (int)Math.floor(y); }
    int blockZ() { return (int)Math.floor(z); }
    boolean contains(double px,double py,double pz) {
        return (int)Math.floor(px)==blockX() && (int)Math.floor(py)==blockY() && (int)Math.floor(pz)==blockZ();
    }
    boolean displaced(double px,double py,double pz) {
        double dx=px-x,dy=py-y,dz=pz-z;
        return !contains(px,py,pz) && dx*dx+dy*dy+dz*dz>=1;
    }
}
