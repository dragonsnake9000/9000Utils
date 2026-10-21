package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LightApproachRulesTest {
    @Test void sixBlockBoundaryIsInclusiveWithoutAcceptingFarCorners() {
        assertTrue(LightApproachRules.nearby(0,257,0,6,257,0));
        assertFalse(LightApproachRules.nearby(0,257,0,6.001,257,0));
        assertFalse(LightApproachRules.nearby(0,257,0,6,257,6));
    }
    @Test void usesActualPlayerPositionIncludingHeightAndNegativeCoordinates() {
        assertTrue(LightApproachRules.nearby(-100.5,257,-100.5,-97.5,259,-96.5));
        assertFalse(LightApproachRules.nearby(-100.5,257,-100.5,-96.5,260,-96.5));
    }
}
