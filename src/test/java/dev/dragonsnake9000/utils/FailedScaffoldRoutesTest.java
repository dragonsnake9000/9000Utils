package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FailedScaffoldRoutesTest {
    @Test void replanningCannotImmediatelyReuseFailedDestination() {
        var routes = new FailedScaffoldRoutes();
        routes.reject(123, 100);
        for (int retry = 0; retry < 100; retry++) assertTrue(routes.rejected(123, 100 + retry));
        assertFalse(routes.rejected(456, 100));
        assertTrue(routes.rejected(123, 60_000_000_099L));
        assertFalse(routes.rejected(123, 60_000_000_100L));
    }
    @Test void sessionResetRemovesOldWorldFailures() {
        var routes = new FailedScaffoldRoutes();
        routes.reject(123, 0);
        routes.clear();
        assertFalse(routes.rejected(123, 1));
    }
}
