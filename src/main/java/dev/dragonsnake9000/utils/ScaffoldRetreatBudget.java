package dev.dragonsnake9000.utils;

/** Limit cumulative repositioning for one destination, even across repeated approach attempts. */
final class ScaffoldRetreatBudget {
    private long destination;
    private int ticks, lastTick = -1;
    boolean allow(long key, int tick) {
        if (key != destination || tick < lastTick) { ticks = 0; lastTick = -1; destination = key; }
        if (tick != lastTick) { ticks++; lastTick = tick; }
        return ticks <= 40;
    }
    void reset() { ticks = 0; lastTick = -1; }
}
