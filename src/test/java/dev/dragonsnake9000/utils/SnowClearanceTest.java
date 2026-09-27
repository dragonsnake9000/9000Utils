package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnowClearanceTest {
    @Test void onlyTheFirstCoverBlockMayBeSnowAndOnlyWhenEnabled() {
        assertTrue(TargetRules.permitsCover(1, false, true, true));
        assertFalse(TargetRules.permitsCover(1, false, true, false));
        assertFalse(TargetRules.permitsCover(2, false, true, true));
        assertFalse(TargetRules.permitsCover(1, false, false, true));
    }
    @Test void snowDoesNotBypassTheRestOfTheAirColumn() {
        assertTrue(TargetRules.hasClearance(64, 320, 10,
            y -> TargetRules.permitsCover(y - 64, y > 65, y == 65, true)));
        assertFalse(TargetRules.hasClearance(64, 320, 10,
            y -> TargetRules.permitsCover(y - 64, y > 65 && y != 70, y == 65, true)));
        assertTrue(TargetRules.hasClearance(64, 320, 0,
            y -> TargetRules.permitsCover(y - 64, false, true, true)));
    }
}
