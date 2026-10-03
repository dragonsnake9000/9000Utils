package dev.dragonsnake9000.utils;

/** Two quiet ticks tolerate the gap between consecutive bites before closing our screen. */
final class EatingInventoryHold {
    private int remaining, quietTicks;
    void start(int ticks) { remaining = Math.max(1, ticks); quietTicks = 0; }
    boolean shouldClose(boolean untilFinished, boolean eating) {
        remaining--;
        quietTicks = eating ? 0 : quietTicks + 1;
        return untilFinished ? quietTicks >= 2 : remaining <= 0;
    }
}
