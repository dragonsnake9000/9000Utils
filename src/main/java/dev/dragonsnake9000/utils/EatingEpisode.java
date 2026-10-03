package dev.dragonsnake9000.utils;

/** Do not repeatedly interrupt a meal when a slot refresh briefly stops item use. */
final class EatingEpisode {
    private boolean handled;
    private int quiet;
    boolean ready(boolean eating) {
        if (eating) quiet = 0;
        else if (++quiet >= 40) handled = false;
        return eating && !handled;
    }
    void handled() { handled = true; }
    void reset() { handled = false; quiet = 0; }
}
