package dev.dragonsnake9000.utils;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerReasonsTest {
    @Test void oldNamesAndInvalidReasonsDisplayUndefined() {
        assertEquals(PlayerReasons.Reason.undefined, PlayerReasons.get(List.of(), "OldName"));
        assertEquals(PlayerReasons.Reason.undefined, PlayerReasons.get(List.of("oldname=bad"), "OldName"));
    }
    @Test void reasonsStayWithAccountsRegardlessOfCaseAndListOrder() {
        var entries = PlayerReasons.set(List.of(), " Attacker ", PlayerReasons.Reason.sword);
        entries = PlayerReasons.set(entries, "Other", PlayerReasons.Reason.crystal);
        assertEquals(PlayerReasons.Reason.sword, PlayerReasons.get(entries, "ATTACKER"));
        assertEquals(PlayerReasons.Reason.crystal, PlayerReasons.get(entries, "other"));
        entries = PlayerReasons.set(entries, "attacker", PlayerReasons.Reason.mace);
        assertEquals(2, entries.size());
        assertEquals(PlayerReasons.Reason.mace, PlayerReasons.get(entries, "Attacker"));
    }
    @Test void clearingReasonDoesNotChangeOtherAccounts() {
        var entries = PlayerReasons.set(List.of("a=sword", "b=crystal"), "A", PlayerReasons.Reason.undefined);
        assertEquals(PlayerReasons.Reason.undefined, PlayerReasons.get(entries, "a"));
        assertEquals(PlayerReasons.Reason.crystal, PlayerReasons.get(entries, "b"));
    }
}
