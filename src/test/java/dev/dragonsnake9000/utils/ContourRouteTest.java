package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContourRouteTest {
    @Test void rejectsShortcutsThroughConeButAllowsPerimeter() {
        var route = new ContourRoute(0, 0, 8, 70);
        assertFalse(route.edge(-10, 68, 0, 10, 68, 0));
        assertTrue(route.edge(10, 68, 0, 10, 68, 1));
        assertFalse(route.edge(10, 70, 0, 10, 71, 1));
        assertTrue(route.edge(10, 68, 0, 10, 67, 1));
    }
    @Test void correctedPositionCanEscapeButCannotMoveDeeper() {
        var route = new ContourRoute(0, 0, 8, 70);
        assertTrue(route.edge(6, 68, 0, 7, 68, 0));
        assertFalse(route.edge(6, 68, 0, 5, 68, 0));
    }
}
