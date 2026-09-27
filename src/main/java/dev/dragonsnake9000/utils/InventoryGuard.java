package dev.dragonsnake9000.utils;
import java.util.List;

final class InventoryGuard {
    private static Object owner;
    private static final List<String> NAMES = List.of("moss-placer", "auto-replenish", "chest-swap");
    static boolean acquire(Object requester) {
        if (owner != null) return owner == requester;
        owner = requester;
        ModulePauses.hold(requester, NAMES);
        return true;
    }
    static void release(Object requester) {
        if (owner != requester) return;
        owner = null;
        ModulePauses.release(requester);
    }
    static void maintain(Object requester) {
        if (owner == requester) ModulePauses.hold(requester, NAMES);
    }
}
