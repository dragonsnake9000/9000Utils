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
    private static final FailedScaffoldRoutes failed = new FailedScaffoldRoutes();
    private static final ScaffoldRetreatBudget retreat = new ScaffoldRetreatBudget();
    private static long retreatDestination;
    private static boolean retreating;

    /** 0: normal movement; 1: repositioning; 2: cannot safely clear the scaffold footprint. */
    public static int reposition(baritone.api.IBaritone baritone, baritone.api.utils.BetterBlockPos src,
                                 baritone.api.utils.BetterBlockPos dest) {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || mc.world == null || walker == null || !walker.isActive()) return 0;
        long key = dest.asLong();
        if (retreatDestination != key) { retreating = false; retreatDestination = key; }
        var cell = dest.down();
        // Only rising steps with a missing support block; never interrupt valid airborne pillars.
        if (dest.y != src.y + 1 || (src.x == dest.x && src.z == dest.z)
            || !mc.world.getBlockState(cell).isReplaceable() || !mc.player.isOnGround()) {
            retreating = false;
            return 0;
        }
        if (!retreating && !mc.player.getBoundingBox().intersects(new Box(cell))) return 0;
        retreating = true;
        double dx = src.x + .5 - mc.player.getX(), dz = src.z + .5 - mc.player.getZ();
        if (dx * dx + dz * dz < .01 && !mc.player.getBoundingBox().intersects(new Box(cell))) {
            retreating = false;
            attempts.reset();
            anchor = null;
            baritone.getInputOverrideHandler().clearAllKeys();
            return 0;
        }
        boolean safe = Math.abs(mc.player.getY() - src.y) < .15 && dx * dx + dz * dz < 2.25
            && RegionConstraint.allows(src.x, src.y, src.z)
            && mc.world.getBlockState(src.down()).isSideSolidFullSquare(mc.world, src.down(), net.minecraft.util.math.Direction.UP);
        // Check the short retreat corridor, not just its endpoint. Keep the player on the existing support.
        for (int step = 1; safe && step <= 4; step++) {
            safe = mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(dx * step / 4, 0, dz * step / 4));
        }
        if (!safe || !retreat.allow(key, mc.player.age)) {
            failed.reject(key, System.nanoTime());
            retreating = false;
            attempts.reset();
            return 2;
        }
        var inputs = baritone.getInputOverrideHandler();
        inputs.clearAllKeys();
        inputs.setInputForceState(Input.SNEAK, true);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        baritone.getLookBehavior().updateTarget(new baritone.api.utils.Rotation(yaw, 0), true);
        // Wait for alignment so an old placement-facing yaw cannot send the player over the edge.
        if (Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(mc.player.getYaw() - yaw)) < 20)
            inputs.setInputForceState(Input.MOVE_FORWARD, true);
        return 1;
    }
    private static long watchedDestination;
    private static net.minecraft.util.math.Vec3d anchor;
    private static int watchStarted, lastAttempt;
    public static boolean rejected(int x, int y, int z) {
        return failed.rejected(net.minecraft.util.math.BlockPos.asLong(x, y, z), System.nanoTime());
    }
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
    public static boolean abandonMovement(baritone.api.utils.BetterBlockPos destination) {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || walker == null || !walker.isActive()) { reset(); return false; }
        long key = destination.asLong();
        if (failed.rejected(key, System.nanoTime())) return true;
        var inputs = BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler();
        var position = mc.player.getPos();
        // Also bound attempts that fail before an interaction packet is sent. Keep the timer
        // across short gaps between right-clicks; actual movement resets it.
        boolean placing = inputs.isInputForcedDown(Input.CLICK_RIGHT)
            || (inputs.isInputForcedDown(Input.SNEAK)
                && mc.world.getBlockState(destination.down()).isReplaceable()
                && mc.player.getBoundingBox().intersects(new Box(destination.down())));
        if (anchor == null || watchedDestination != key || anchor.squaredDistanceTo(position) > .04
            || mc.player.age < watchStarted || mc.player.age - lastAttempt > 40) {
            anchor = position; watchedDestination = key; watchStarted = mc.player.age;
        }
        if (placing) lastAttempt = mc.player.age;
        boolean stalled = placing && mc.player.age - watchStarted >= 40;
        if (!attempts.consume() && !stalled) return false;
        // Reject this destination in A*, not just the current executor. All incoming approaches
        // are excluded briefly so a different edge cannot retry the same bad scaffold immediately.
        failed.reject(key, System.nanoTime());
        anchor = null;
        PathingSafety.suspendScaffolding();
        return true;
    }
    static void reset() { attempts.reset(); failed.clear(); anchor = null; retreat.reset(); retreating = false; }
}
