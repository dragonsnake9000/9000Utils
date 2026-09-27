package dev.dragonsnake9000.utils;

import baritone.api.BaritoneAPI;
import baritone.api.utils.input.Input;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import java.util.List;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Pause placement before the first mining interaction, including newly changed terrain. */
public final class MiningGuard {
    private static final Object OWNER = new Object();
    private static int releaseAt;
    private static boolean held;
    private static boolean enabled() {
        LavacastPathfinder walker = Modules.get().get(LavacastPathfinder.class);
        MossRefill refill = Modules.get().get(MossRefill.class);
        return ((walker != null && walker.isActive()) || (refill != null && refill.transactionActive()))
            && mc.player != null && mc.world != null;
    }
    public static boolean beforeBreak(BlockPos pos) {
        if (!enabled()) return false;
        var inputs = BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler();
        if (!inputs.isInputForcedDown(Input.CLICK_LEFT)) return false;
        // Also veto execution: a previously clear path can acquire moss after calculation.
        if ((!PathingSafety.mayBreakMoss() && mc.world.getBlockState(pos).isOf(Blocks.MOSS_BLOCK))
            || PathingSafety.protectsSurface(mc.world.getBlockState(pos).getBlock())) {
            inputs.setInputForceState(Input.CLICK_LEFT, false);
            mc.interactionManager.cancelBlockBreaking();
            return true;
        }
        held = true;
        releaseAt = mc.player.age + 10;
        ModulePauses.hold(OWNER, List.of("moss-placer"));
        return false;
    }
    @EventHandler(priority = 100) private void tick(TickEvent.Pre event) {
        if (!enabled()) { release(); return; }
        if (!held) return;
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT))
            releaseAt = mc.player.age + 10;
        if (mc.player.age < releaseAt) ModulePauses.hold(OWNER, List.of("moss-placer"));
        else release();
    }
    static void release() { ModulePauses.release(OWNER); held = false; releaseAt = 0; }
}
