package dev.dragonsnake9000.utils;

/** A brief pause caused by our restart must not begin another restart countdown. */
final class AutoEatRestartTimer {
    private int elapsed, quiet;
    private boolean started, fired;
    boolean update(boolean eating) {
        if (eating) { quiet = 0; started = true; }
        else if (++quiet >= 40) { reset(); return false; }
        if (!started || fired) return false;
        if (++elapsed >= 20 && eating) { fired = true; return true; }
        return false;
    }
    void reset() { elapsed = quiet = 0; started = fired = false; }
}
