package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlayerNameListTest {
    @Test void matchesExactAccountsIgnoringCaseAndAccidentalWhitespace() {
        assertTrue(PlayerNameList.matches(List.of("  Threat_Name  "), "threat_name"));
        assertFalse(PlayerNameList.matches(List.of("Threat"), "Threat_Name"));
        assertFalse(PlayerNameList.matches(List.of(), "Threat"));
    }
}
