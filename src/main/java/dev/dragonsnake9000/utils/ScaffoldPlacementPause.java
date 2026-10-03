package dev.dragonsnake9000.utils;

import baritone.api.BaritoneAPI;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import java.util.List;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Pause the competing placer before Baritone prepares a missing bridge/pillar support. */
public final class ScaffoldPlacementPause {
    private static final Object OWNER = new Object();
    private static Object world;
    private static int until;
    private static boolean held;
    private static Object currentMovement, buildingMovement;

    public static void beforeMovement(Object movement, BlockPos support) {
        currentMovement = movement;
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || mc.world == null || walker == null || !walker.isActive()
            || !BaritoneAPI.getSettings().allowPlace.value) return;
        if (buildingMovement == movement || (support != null && mc.world.getBlockState(support).isReplaceable())) {
            buildingMovement = movement;
            hold();
        }
    }

    /** Also catch builder/bridge placement requests that have no missing support marker. */
    public static void buildingIntent() {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || mc.world == null || walker == null || !walker.isActive()) return;
        buildingMovement = currentMovement;
        hold();
    }
    private static void hold() {
        world = mc.world;
        until = mc.player.age + 10;
        held = true;
        ModulePauses.hold(OWNER, List.of("moss-placer"));
    }

    @EventHandler(priority = 1400) private void tick(TickEvent.Pre event) {
        if (!held) return;
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || mc.world != world || walker == null || !walker.isActive()
            || mc.player.age >= until) { reset(); return; }
        // Keep our lease independent of eating, mining and refill pauses.
        ModulePauses.hold(OWNER, List.of("moss-placer"));
    }

    public static void reset() {
        ModulePauses.release(OWNER);
        held = false; world = null; until = 0;
        currentMovement = buildingMovement = null;
    }
}
