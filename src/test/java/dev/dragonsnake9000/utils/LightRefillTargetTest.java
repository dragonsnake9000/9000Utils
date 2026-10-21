package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LightRefillTargetTest {
    @Test void acceptsOneFullStackButNotASecond() {
        assertEquals(64,LightRefillTarget.BLOCKS);
        assertFalse(LightRefillTarget.wouldOverfill(0,64));
        assertTrue(LightRefillTarget.wouldOverfill(64,64));
    }
    @Test void fillsMatchingPartialToReachExactlyOneStack() {
        assertTrue(LightRefillTarget.wouldOverfill(16,64));
        assertTrue(LightRefillTarget.mayFillPartial(16,16,64));
    }
    @Test void mixedLightTypesCannotOverfillByMergingALargePartial() {
        assertFalse(LightRefillTarget.mayFillPartial(36,10,64));
        assertTrue(LightRefillTarget.mayFillPartial(40,40,64));
        assertFalse(LightRefillTarget.mayFillPartial(64,63,64));
    }
}
