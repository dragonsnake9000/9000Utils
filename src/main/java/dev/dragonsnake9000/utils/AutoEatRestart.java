package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.orbit.EventHandler;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Restart only an already-enabled Meteor Auto Eat, once per observed eating episode. */
final class AutoEatRestart {
    private final AutoEatRestartTimer timer = new AutoEatRestartTimer();
    private Object world;
    @EventHandler private void tick(TickEvent.Post event) {
        var walker = Modules.get().get(LavacastPathfinder.class);
        var eat = Modules.get().get(AutoEat.class);
        if (mc.player == null || mc.world == null || !mc.player.isAlive() || walker == null
            || !walker.isActive() || !walker.restartAutoEatOnce() || eat == null || !eat.isActive()) {
            timer.reset(); world = null; return;
        }
        if (world != mc.world) { timer.reset(); world = mc.world; }
        if (!timer.update(eat.eating)) return;
        // Both toggles happen together; no delayed re-enable can override a user's later choice.
        eat.toggle();
        if (!eat.isActive()) eat.toggle();
    }
}
