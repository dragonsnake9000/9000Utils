package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

public class RusherHackFriendsTest {
    public interface Relations { boolean isFriend(String name); }
    public static class FakeApi {
        static final Set<String> friends = new HashSet<>();
        public static Relations getRelationManager() { return friends::contains; }
    }
    @Test void liveFriendsAreRecognizedAndRemovalTakesEffect() {
        var bridge = new RusherHackFriends(FakeApi.class.getName());
        FakeApi.friends.clear();
        assertFalse(bridge.isFriend("Ally", message -> fail(message)));
        FakeApi.friends.add("Ally");
        assertTrue(bridge.isFriend("Ally", message -> fail(message)));
        assertFalse(bridge.isFriend("Stranger", message -> fail(message)));
        FakeApi.friends.remove("Ally");
        assertFalse(bridge.isFriend("Ally", message -> fail(message)));
    }
    @Test void missingClientIsOptional() {
        var bridge = new RusherHackFriends("not.installed.RusherHackAPI");
        assertFalse(bridge.isFriend("Ally", message -> fail(message)));
    }
}
