package dev.dragonsnake9000.utils;

import java.util.concurrent.ConcurrentHashMap;

/** Shared by the client thread and Baritone's search workers. Expiry uses a monotonic clock. */
final class FailedScaffoldRoutes {
    private final ConcurrentHashMap<Long, Long> destinations = new ConcurrentHashMap<>();
    void reject(long destination, long now) {
        destinations.entrySet().removeIf(entry -> entry.getValue() <= now);
        destinations.put(destination, now + 60_000_000_000L);
    }
    boolean rejected(long destination, long now) {
        Long until = destinations.get(destination);
        return until != null && now < until;
    }
    void clear() { destinations.clear(); }
}
