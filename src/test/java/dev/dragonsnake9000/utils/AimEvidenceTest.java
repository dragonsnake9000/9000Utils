package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AimEvidenceTest {
    @Test void lookingDownAtBotSupportsDive() {
        double angle = AimEvidence.angle(0, -1, 0, 0, -10, 0);
        assertEquals(0, angle, 1e-8);
        assertTrue(AimEvidence.supports(angle, true));
    }
    @Test void lookingAwayOrOccludedDoesNotSupplyAimEvidence() {
        assertFalse(AimEvidence.supports(AimEvidence.angle(0, 1, 0, 0, -10, 0), true));
        assertFalse(AimEvidence.supports(0, false));
        assertFalse(AimEvidence.supports(16, true));
        assertTrue(AimEvidence.supports(14, true));
    }
    @Test void invalidAndZeroVectorsAreNotEvidence() {
        assertFalse(AimEvidence.supports(AimEvidence.angle(0, 0, 0, 0, -10, 0), true));
        assertFalse(AimEvidence.supports(AimEvidence.angle(0, -1, 0, 0, 0, 0), true));
        assertFalse(AimEvidence.supports(Double.NaN, true));
    }
}
