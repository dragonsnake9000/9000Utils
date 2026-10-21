package dev.dragonsnake9000.utils;
/** Restrict lighting detours to an actual six-block sphere, never an unbounded roof tour. */
final class LightApproachRules {
    static boolean nearby(double px,double py,double pz,double x,double y,double z) {
        double dx=px-x,dy=py-y,dz=pz-z;return dx*dx+dy*dy+dz*dz<=36;
    }
}
