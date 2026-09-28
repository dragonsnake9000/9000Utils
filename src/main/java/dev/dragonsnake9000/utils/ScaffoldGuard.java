package dev.dragonsnake9000.utils;

import baritone.api.BaritoneAPI;
import baritone.api.utils.input.Input;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Reject self-intersecting scaffolds, but allow valid jump/pillar placements below the player's feet. */
public final class ScaffoldGuard {
    private static final PlacementRetryBudget attempts = new PlacementRetryBudget();
    public static boolean beforePlace(Hand hand, BlockHitResult hit) {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || walker == null || !walker.isActive() || !mc.player.getStackInHand(hand).isOf(Items.MOSS_BLOCK)) return false;
        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (!baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_RIGHT)) return false;
        var context = new ItemPlacementContext(mc.player, hand, mc.player.getStackInHand(hand), hit);
        var cell = context.getBlockPos();
        boolean collision = mc.player.getBoundingBox().intersects(new Box(cell));
        attempts.record(cell.asLong(), mc.player.age, collision);
        return collision;
    }
    public static boolean abandonMovement() {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || walker == null || !walker.isActive()) { reset(); return false; }
        if (!attempts.consume()) return false;
        PathingSafety.suspendScaffolding();
        return true;
    }
    static void reset() { attempts.reset(); }
}
