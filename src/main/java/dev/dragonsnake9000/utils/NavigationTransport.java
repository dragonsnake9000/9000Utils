package dev.dragonsnake9000.utils;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.util.math.BlockPos;

/** All navigation uses the pinned Baritone Java API; chat interception has been removed. */
public final class NavigationTransport {
    public static void go(BlockPos feet) { BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(feet)); }
    public static void stop() {
        var mc = meteordevelopment.meteorclient.MeteorClient.mc;
        boolean preserveUse = ActivityPause.eatingPauseActive() && mc.options.useKey.isPressed();
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        if (preserveUse) mc.options.useKey.setPressed(true);
    }
}
