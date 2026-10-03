package dev.dragonsnake9000.utils;

/** Only retry when the food count fails to decrease; cap retries for one stalled eating episode. */
final class EatingRecoveryTimer {
    private int lastCount = -1, ticks, attempts;
    boolean update(boolean eating, int count) {
        if (!eating || count <= 0) { reset(); return false; }
        if (lastCount < 0 || count < lastCount) { ticks = 0; attempts = 0; }
        lastCount = count;
        if (++ticks < 100 || attempts >= 3) return false;
        ticks = 0; attempts++;
        return true;
    }
    int attempts() { return attempts; }
    void reset() { lastCount = -1; ticks = attempts = 0; }
}
