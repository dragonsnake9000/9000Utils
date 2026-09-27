package dev.dragonsnake9000.utils;
import java.util.Objects;

/** Cancelling never resets the rate limit; repeated scans cannot resend the same active goal. */
final class NavigationBudget<T> {
    private T issued;
    private long lastDispatch = Long.MIN_VALUE / 2;
    boolean canDispatch(long now, T goal, int minimumTicks) {
        return goal != null && !Objects.equals(issued, goal) && now - lastDispatch >= Math.max(1, minimumTicks);
    }
    void dispatched(long now, T goal) { lastDispatch = now; issued = goal; }
    void clear() { issued = null; }
    void reset() { issued = null; lastDispatch = Long.MIN_VALUE / 2; }
}
