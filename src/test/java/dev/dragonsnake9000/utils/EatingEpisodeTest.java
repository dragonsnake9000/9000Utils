package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EatingEpisodeTest {
    @Test void refreshCannotLoopWhenItBrieflyInterruptsEating() {
        var episode = new EatingEpisode();
        assertTrue(episode.ready(true));
        episode.handled();
        for (int i = 0; i < 100; i++) {
            assertFalse(episode.ready(false));
            assertFalse(episode.ready(true));
        }
        for (int i = 0; i < 40; i++) assertFalse(episode.ready(false));
        assertTrue(episode.ready(true));
    }
    @Test void busyInventoryCanRetryBeforeAnyTransfer() {
        var episode = new EatingEpisode();
        assertTrue(episode.ready(true));
        assertTrue(episode.ready(true));
        episode.handled();
        assertFalse(episode.ready(true));
        episode.reset();
        assertTrue(episode.ready(true));
    }
}
