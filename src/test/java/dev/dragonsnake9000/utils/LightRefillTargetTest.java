package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LightRefillTargetTest {
    @Test void acceptsTwoFullStacksButNotAThird() {
        assertEquals(128,LightRefillTarget.BLOCKS);
        assertFalse(LightRefillTarget.wouldOverfill(64,64));
        assertTrue(LightRefillTarget.wouldOverfill(128,64));
    }
    @Test void fillsMatchingPartialToReachExactlyTwoStacks() {
        assertTrue(LightRefillTarget.wouldOverfill(80,64));
        assertTrue(LightRefillTarget.mayFillPartial(80,16,64));
    }
    @Test void mixedLightTypesCannotOverfillByMergingALargePartial() {
        assertFalse(LightRefillTarget.mayFillPartial(100,10,64));
        assertTrue(LightRefillTarget.mayFillPartial(100,40,64));
        assertFalse(LightRefillTarget.mayFillPartial(128,63,64));
    }
}
