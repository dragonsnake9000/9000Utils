package dev.dragonsnake9000.utils;

import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.EventHandler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Supply specializations depend on Orbit discovering private handlers in their shared parent. */
class InheritedEventsTest {
    static class Parent { int events; @EventHandler private void onEvent(String event) { events++; } }
    static class Child extends Parent { }
    @Test void orbitDispatchesInheritedHandlers() {
        var bus = new EventBus();
        bus.registerLambdaFactory("dev.dragonsnake9000.utils", (lookup, klass) ->
            (java.lang.invoke.MethodHandles.Lookup) lookup.invoke(null, klass, java.lang.invoke.MethodHandles.lookup()));
        Child child = new Child(); bus.subscribe(child); bus.post("tick");
        assertEquals(1, child.events);
    }
}
