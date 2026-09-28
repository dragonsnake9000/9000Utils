package dev.dragonsnake9000.utils;

/** Count consecutive collisions at one placement cell; a valid placement resets the budget. */
final class PlacementRetryBudget {
    private long cell;
    private int start, last, count;
    private boolean blocked;
    void record(long position, int tick, boolean collision) {
        if (!collision) { count = 0; blocked = false; return; }
        if (count == 0 || cell != position || tick < last || tick - last > 40) {
            cell = position; start = tick; count = 0; blocked = false;
        }
        if (count == 0 || tick != last) count++;
        last = tick;
        if (count >= 5 && tick - start >= 20) blocked = true;
    }
    boolean consume() { boolean result = blocked; blocked = false; if (result) count = 0; return result; }
    void reset() { count = 0; blocked = false; }
}
