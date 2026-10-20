package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RefillAnchorTest {
    @Test void horizontalKnockbackTriggersReturn() {
        var anchor=new RefillAnchor(10.5,257,20.5);
        assertTrue(anchor.displaced(11.5,257,20.5));
        assertFalse(anchor.contains(11.5,257,20.5));
    }
    @Test void smallNudgesInsideStandingBlockDoNotCausePathSpam() {
        var anchor=new RefillAnchor(10.2,257,20.2);
        assertFalse(anchor.displaced(10.8,257,20.8));
        assertTrue(anchor.contains(10.8,257,20.8));
    }
    @Test void fallingOffTheStandingBlockIsDisplacement() {
        assertTrue(new RefillAnchor(0.5,257,0.5).displaced(0.5,256,0.5));
    }
    @Test void negativeCoordinatesRoundDown() {
        var anchor=new RefillAnchor(-0.2,257,-5.8);
        assertEquals(-1,anchor.blockX());assertEquals(-6,anchor.blockZ());
        assertTrue(anchor.contains(-0.8,257,-5.2));
    }
}
