package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BoundedRouteSearchTest {
    @Test void choosesWalkingCostInsteadOfNearestDestinationName() {
        Map<String,List<String>> graph=Map.of("start",List.of("near","detour"),"detour",List.of("far"));
        var search=new BoundedRouteSearch<>("start",n->graph.getOrDefault(n,List.of()),
            (a,b)->b.equals("near")?10:1,n->n.equals("near")||n.equals("far"),20,100);
        assertFalse(search.step(1));
        while(!search.step(1)){}
        assertEquals(List.of("start","detour","far"),search.route());
    }
    @Test void doesNotAcceptRouteLongerThanRecordedTrail() {
        var search=new BoundedRouteSearch<>(0,n->List.of(n+1),(a,b)->1,n->n==4,3,100);
        assertTrue(search.step(100));assertTrue(search.route().isEmpty());
    }
    @Test void acceptsOriginalPositionWhenAlreadyAtMossWork() {
        var search=new BoundedRouteSearch<>(0,n->List.of(n+1),(a,b)->1,n->n==0,0,10);
        assertTrue(search.step(1));assertEquals(List.of(0),search.route());
    }
    @Test void nodeLimitBoundsSearchEvenOnUnendingTerrain() {
        var search=new BoundedRouteSearch<>(0,n->List.of(n+1),(a,b)->1,n->false,1000,5);
        assertTrue(search.step(10));assertTrue(search.route().isEmpty());
    }
    @Test void relaxesAlreadyDiscoveredExpensiveRoute() {
        Map<String,List<String>> graph=Map.of("start",List.of("middle","step"),"step",List.of("middle"),"middle",List.of("goal"));
        var search=new BoundedRouteSearch<>("start",n->graph.getOrDefault(n,List.of()),
            (a,b)->a.equals("start")&&b.equals("middle")?8:1,n->n.equals("goal"),10,100);
        while(!search.step(1)){}
        assertEquals(List.of("start","step","middle","goal"),search.route());
    }
}
