package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Bounded emergency exception to normal no-moss-mining: clear one exit, move out, then resume. */
final class MossEscape {
    private record Exit(BlockPos feet, List<BlockPos> moss) {}
    private static final Object OWNER = new Object();
    private static Exit exit;
    private static Object world;
    private static int confinedTicks, scanTicks, elapsed, settling, oldSlot = -1, toolSlot = -1;
    private static boolean moving;
    static boolean busy() { return exit != null; }
    static void reset() {
        if (exit != null) NavigationTransport.stop();
        if (mc.interactionManager != null && exit != null) mc.interactionManager.cancelBlockBreaking();
        if (mc.player != null && oldSlot >= 0 && mc.player.getInventory().selectedSlot == toolSlot) InvUtils.swap(oldSlot, false);
        PathingSafety.release(OWNER);
        InventoryGuard.release(OWNER);
        exit = null; world = null; confinedTicks = scanTicks = elapsed = settling = 0;
        oldSlot = toolSlot = -1; moving = false;
    }
    @EventHandler(priority = -90) private void tick(TickEvent.Pre event) {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || mc.world == null || walker == null || !walker.isActive() || !walker.emergencyMossEscape()
            || !mc.player.isAlive() || (world != null && world != mc.world)) { reset(); return; }
        if (busy()) InventoryGuard.maintain(OWNER);
        if (ActivityPause.isPaused() || AutomationContext.blockedScreen()) {
            if (busy()) { NavigationTransport.stop(); mc.interactionManager.cancelBlockBreaking(); moving = false; }
            return;
        }
        if (!busy()) {
            var refill = Modules.get().get(MossRefill.class);
            world = mc.world;
            if (refill != null && refill.transactionActive()) { confinedTicks = 0; return; }
            // Moving or jumping inside the same small pocket no longer resets the detector.
            if (++scanTicks < 10 || !MossInventory.playerInventoryReady()) return;
            scanTicks = 0;
            int limit = walker.mossEscapeReachableBlocks();
            int reachable = reachableCount(mc.player.getBlockPos(), limit, Set.of());
            if (reachable < 0 || reachable > limit) { confinedTicks = 0; return; }
            if ((confinedTicks += 10) < 80) return;
            Exit candidate = findExit(limit, reachable);
            if (candidate == null || candidate.moss.isEmpty() || !InventoryGuard.acquire(OWNER)) return;
            walker.pauseForRefill();
            NavigationTransport.stop();
            PathingSafety.acquire(OWNER);
            exit = candidate; world = mc.world; oldSlot = mc.player.getInventory().selectedSlot;
            elapsed = settling = 0; moving = false;
            walker.info("Confined to at most %s reachable positions. Pausing placement to clear a moss exit.", limit);
        }
        if (++elapsed > 400) {
            walker.error("Moss escape timed out. Automation stopped; check the surrounding blocks.");
            ModulePauses.suppressRestore("moss-placer");
            walker.toggle();
            reset();
            return;
        }
        List<BlockPos> remaining = exit.moss.stream().filter(pos -> mc.world.getBlockState(pos).isOf(Blocks.MOSS_BLOCK)).toList();
        if (!remaining.isEmpty()) {
            moving = false;
            for (BlockPos pos : remaining) {
                Vec3d eye = mc.player.getEyePos(), center = Vec3d.ofCenter(pos);
                if (eye.squaredDistanceTo(center) > Math.pow(mc.player.getBlockInteractionRange() - .2, 2)) continue;
                // Rays entirely inside a block can miss its faces. A body-overlapping moss
                // block is still a legitimate nearby mining target, with no through-wall reach.
                final var hit = new Box(pos).contains(eye)
                    ? new net.minecraft.util.hit.BlockHitResult(center, Direction.UP, pos, true)
                    : mc.world.raycast(new RaycastContext(eye, center, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) continue;
                if (!MossInventory.playerInventoryReady()) return;
                int best = mc.player.getInventory().selectedSlot; float speed = 0;
                for (int slot = 0; slot < 9; slot++) {
                    float candidate = mc.player.getInventory().getStack(slot).getMiningSpeedMultiplier(mc.world.getBlockState(pos));
                    if (candidate > speed) { speed = candidate; best = slot; }
                }
                toolSlot = best; InvUtils.swap(best, false);
                Rotations.rotate(Rotations.getYaw(hit.getPos()), Rotations.getPitch(hit.getPos()), 100, () -> {
                    if (!busy() || world != mc.world || ActivityPause.isPaused() || AutomationContext.blockedScreen()
                        || !mc.world.getBlockState(pos).isOf(Blocks.MOSS_BLOCK)) return;
                    mc.interactionManager.updateBlockBreakingProgress(pos, hit.getSide());
                    mc.player.swingHand(Hand.MAIN_HAND);
                });
                return;
            }
            return;
        }
        mc.interactionManager.cancelBlockBreaking();
        if (mc.player.getBlockPos().equals(exit.feet) && mc.player.isOnGround()) {
            NavigationTransport.stop();
            if (++settling >= 10) reset(); // Remain paused AFTER moving out, not just after breaking.
        } else {
            settling = 0;
            var behavior = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
            if (!moving || (elapsed % 40 == 0 && !behavior.isPathing() && behavior.getInProgress().isEmpty())) {
                PathingSafety.acquire(OWNER); // Only the explicitly cleared route; no additional mining/building.
                NavigationTransport.go(exit.feet);
                moving = true;
            }
        }
    }
    private static int reachableCount(BlockPos origin, int limit, Set<BlockPos> removed) {
        return ReachableSpace.count(new ReachableSpace.Pos(origin.getX(), origin.getY(), origin.getZ()), limit, p -> {
            BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
            // Missing terrain and unusual collision shapes cannot prove confinement.
            if (!mc.world.isChunkLoaded(pos) || !mc.world.getWorldBorder().contains(pos)
                || pos.getY() < mc.world.getBottomY() || pos.getY() >= mc.world.getTopYInclusive() + 1)
                return ReachableSpace.Cell.Unknown;
            if (removed.contains(pos)) return ReachableSpace.Cell.Open;
            var state = mc.world.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) return ReachableSpace.Cell.Unknown;
            if (state.getCollisionShape(mc.world, pos).isEmpty()) return ReachableSpace.Cell.Open;
            return state.isFullCube(mc.world, pos) ? ReachableSpace.Cell.Solid : ReachableSpace.Cell.Unknown;
        });
    }
    private static Exit findExit(int limit, int reachable) {
        BlockPos origin = mc.player.getBlockPos();
        Exit best = null;
        for (Direction direction : Direction.Type.HORIZONTAL) for (int dy : new int[] {0, 1, -1}) for (int distance = 1; distance <= 3; distance++) {
            BlockPos destination = origin.offset(direction, distance).up(dy);
            Set<BlockPos> clear = new LinkedHashSet<>(List.of(origin, origin.up()));
            boolean supported = true;
            for (int step = 1; step <= distance; step++) {
                BlockPos feet = origin.offset(direction, step).up(dy);
                if (!RegionConstraint.allows(feet.getX(), feet.getY(), feet.getZ()) || !mc.world.getWorldBorder().contains(feet)
                    || !mc.world.getBlockState(feet.down()).isSideSolidFullSquare(mc.world, feet.down(), Direction.UP)) {
                    supported = false; break;
                }
                clear.add(feet); clear.add(feet.up());
            }
            if (!supported) continue;
            if (dy > 0) clear.add(origin.up(2));
            List<BlockPos> moss = new ArrayList<>(); boolean valid = true;
            for (BlockPos pos : clear) {
                var state = mc.world.getBlockState(pos);
                if (state.isOf(Blocks.MOSS_BLOCK)) moss.add(pos);
                else if (!state.getCollisionShape(mc.world, pos).isEmpty() || !state.getFluidState().isEmpty()) { valid = false; break; }
            }
            if (!valid || moss.size() > 4) continue;
            // An open neighboring pocket no longer vetoes recovery: moss may block the way beyond it.
            if (moss.isEmpty()) continue;
            // Do not mine arbitrary nearby moss: clearing this route must release the pocket.
            if (!ReachableSpace.opensExit(reachable, reachableCount(origin, limit, new HashSet<>(moss)), limit)) continue;
            if (best == null || moss.size() < best.moss.size()
                || (moss.size() == best.moss.size() && destination.getSquaredDistance(origin) > best.feet.getSquaredDistance(origin)))
                best = new Exit(destination, List.copyOf(moss));
        }
        return best;
    }
}
