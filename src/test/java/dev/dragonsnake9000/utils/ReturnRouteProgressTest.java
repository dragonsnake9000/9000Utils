package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReturnRouteProgressTest {
    @Test void advancesPastOvershotWaypoints() {
        Deque<Integer> route=new ArrayDeque<>(List.of(1,2,3,4));
        assertTrue(ReturnRouteProgress.advance(route,3));
        assertEquals(List.of(4),List.copyOf(route));
    }
    @Test void reachingDestinationClearsOldWaypoints() {
        Deque<Integer> route=new ArrayDeque<>(List.of(1,2,3));
        assertTrue(ReturnRouteProgress.advance(route,3));assertTrue(route.isEmpty());
    }
    @Test void removesTrailLoopsInsteadOfWalkingThemAgain() {
        Deque<Integer> route=new ArrayDeque<>(List.of(1,2,1,3));
        assertTrue(ReturnRouteProgress.advance(route,1));assertEquals(List.of(3),List.copyOf(route));
    }
    @Test void offRoutePositionDoesNotDiscardNeededRoute() {
        Deque<Integer> route=new ArrayDeque<>(List.of(1,2,3));
        assertFalse(ReturnRouteProgress.advance(route,9));assertEquals(List.of(1,2,3),List.copyOf(route));
    }
}
