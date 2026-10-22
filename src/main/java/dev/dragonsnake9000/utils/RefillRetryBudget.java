package dev.dragonsnake9000.utils;
/** Shared per-box budget: the original failure can schedule at most ten recovery attempts. */
final class RefillRetryBudget {
    private int attempts;
    boolean next(){if(attempts>=10)return false;attempts++;return true;}
    int attempts(){return attempts;}
    void reset(){attempts=0;}
}
