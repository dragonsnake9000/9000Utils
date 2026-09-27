package dev.dragonsnake9000.utils;

import net.minecraft.util.math.Vec3i;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PositionCompatibilityTest {
    @Test void plainVectorDoesNotThrowTheReportedBlockPosCast() {
        assertTrue(PositionCompatibility.matches(0, 0, 0, Vec3i.ZERO));
        assertFalse(PositionCompatibility.matches(17, 70, -31, Vec3i.ZERO));
        assertTrue(PositionCompatibility.matches(17, 70, -31, new Vec3i(17, 70, -31)));
    }
    @Test void unrelatedObjectsAndNullAreUnequal() {
        assertFalse(PositionCompatibility.matches(0, 0, 0, null));
        assertFalse(PositionCompatibility.matches(0, 0, 0, "0,0,0"));
    }
}
