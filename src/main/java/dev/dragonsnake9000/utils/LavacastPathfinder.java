package dev.dragonsnake9000.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.SnowBlock;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.*;

public final class LavacastPathfinder extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<List<Block>> targetBlocks = general.add(new BlockListSetting.Builder().name("target-blocks")
        .description("Blocks to visit. Neighbors count any selected block. Change this list at any time.")
        .defaultValue(Blocks.COBBLESTONE, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN).onChanged(value -> resetTargets()).build());
    private final Setting<Integer> radius = integer("scan-radius", "Primary working radius. Nearby work always wins over the fallback radius.", 30, 1, 256);
    private final Setting<Integer> fallbackRadius = integer("fallback-scan-radius", "Expanded search radius after local work runs out; never smaller than the primary radius.", 100, 1, 512);
    private final Setting<Integer> fallbackDelay = integer("fallback-delay-seconds", "Seconds without eligible primary-radius work before expanding.", 3, 0, 3600);
    private final Setting<Integer> vertical = integer("vertical-range", "Search above and below your feet, within world limits.", 16, 0, 1024);
    private final Setting<Integer> air = integer("air-blocks", "Required air column; 0 removes the extra height requirement, but the top must still be air.", 30, 0, 1024);
    private final Setting<Boolean> snow = general.add(new BoolSetting.Builder().name("allow-snow-layers")
        .description("Allow a snow-layer block directly above a target. Other clearance must still be air; does not include snow blocks or powder snow.")
        .defaultValue(true).onChanged(value -> resetTargets()).build());
    private final Setting<Integer> neighbors = integer("minimum-neighbors", "Required selected-block neighbors among all 26 adjacent positions. Zero allows isolated blocks.", 3, 0, 10);
    private final Setting<Double> clusterWeight = general.add(new DoubleSetting.Builder().name("cluster-preference")
        .description("How strongly dense target-block patches improve the distance score. Zero chooses by distance only.")
        .defaultValue(1.4430955993930203).range(0, 10).sliderRange(0, 3).build());
    private final Setting<Integer> refresh = integer("refresh-ticks", "Delay between scan cycles; scanning does not cancel paths.", 2, 0, 1200);
    private final Setting<Integer> scanBudget = integer("scan-budget", "Maximum block positions checked per tick; large radii take multiple ticks.", 8192, 256, 131072);
    private final Setting<Integer> goalDelay = integer("goal-command-delay-ticks", "Minimum spacing between new Baritone goals. Zero still limits dispatch to once per tick.", 2, 0, 1200);
    private final Setting<Integer> reconsider = integer("retarget-ticks", "Reconsider a better nearby target at this interval, without resending unchanged goals.", 10, 1, 1200);
    private final Setting<Integer> improvement = integer("switch-improvement-percent", "A new target must score this much better to interrupt a working path.", 25, 0, 95);
    private final Setting<Integer> maxAttempt = integer("max-target-seconds", "Abandon a target after this much active time, even while moving. Zero disables the limit.", 12, 0, 3600);
    private final Setting<Integer> stall = integer("stuck-timeout-seconds", "Abandon targets without meaningful progress toward them. Zero disables this check.", 2, 0, 3600);
    private final Setting<Integer> failedDelay = integer("failed-target-cooldown-seconds", "Temporarily ignore failed, stuck, overlong, or excessively indirect targets.", 30, 0, 86400);
    private final Setting<Integer> revisit = integer("revisit-delay-seconds", "Skip reached targets for this long.", 58, 0, 86400);
    private final Setting<Double> detourRatio = general.add(new DoubleSetting.Builder().name("max-detour-ratio")
        .description("Reject routes much longer than the direct distance (with 12 blocks of tolerance). Zero disables this check.")
        .defaultValue(2.5).range(0, 100).sliderRange(0, 10).build());
    private final Setting<Boolean> breakBlocks = bool("allow-break", "Allow Baritone to break blocks to escape holes and navigate.", false);
    private final Setting<Boolean> scaffold = bool("moss-scaffold", "Allow Baritone to build paths using only moss blocks from the hotbar.", true);
    private final Setting<Boolean> combatPause = bool("pause-for-killaura", "Pause movement, placing, refilling and recovery while KillAura attacks.", true);
    private final Setting<Boolean> eatingPause = bool("pause-for-eating", "Pause movement, placing, refilling and recovery while eating or drinking.", true);
    private final Setting<Integer> resumeTicks = integer("activity-resume-delay-ticks", "Quiet time after eating/combat before automation resumes.", 15, 0, 1200);
    private final Setting<Boolean> breakMoss = bool("allow-break-moss", "Allow moss mining; pause Sleepy's placer during breaking and for half a second afterward.", false);
    private final Setting<Boolean> escapeMoss = bool("emergency-moss-escape", "When confined to a small connected space, clear a moss exit into a larger area, even with normal breaking disabled.", true);
    private final Setting<Integer> escapeSpace = general.add(new IntSetting.Builder().name("moss-escape-reachable-blocks")
        .description("Maximum unique reachable standing positions across all directions to count as trapped. Revisiting a position does not increase this count.")
        .defaultValue(20).range(1, 500).sliderRange(1, 100).visible(escapeMoss::get).build());
    private final Setting<Boolean> eatingRecovery = bool("eating-inventory-recovery", "If eating consumes no food for five seconds, open your inventory for recovery. Requires Pause for Eating. Maximum three retries per stalled episode.", false);
    private final Setting<Boolean> eatingKeepOpen = general.add(new BoolSetting.Builder().name("keep-inventory-open-while-eating")
        .description("Once recovery opens inventory, keep it open until eating finishes, instead of using a fixed duration.")
        .defaultValue(false).visible(eatingRecovery::get).build());
    private final Setting<Integer> eatingOpenTicks = general.add(new IntSetting.Builder().name("eating-inventory-open-ticks")
        .description("How many ticks recovery keeps inventory open. 20 ticks is about one second.")
        .defaultValue(2).range(1, 1200).sliderRange(1, 200).visible(() -> eatingRecovery.get() && !eatingKeepOpen.get()).build());
    private final Setting<Boolean> ping = bool("out-of-moss-ping", "Play the arrow-hit sound when all moss supplies run out.", true);
    private final Setting<Boolean> dropEmpty = bool("drop-empty-shulkers", "Throw out truly empty inventory shulkers only while this pathfinder is active.", true);
    private final Setting<Boolean> restart = bool("periodic-restart", "Periodically stop the current path and choose a fresh target without clearing failed-target memory.", true);
    private final Setting<Integer> restartSeconds = general.add(new IntSetting.Builder().name("restart-interval-seconds")
        .description("Time between stop/start cycles; zero disables the timer.").defaultValue(3).range(0, 100).sliderRange(0, 100).visible(restart::get).build());
    private final Setting<Boolean> helperRefill = bool("enable-moss-refill", "Enable the shulker refill module with this pathfinder.", true);
    private final Setting<Boolean> helperArmor = bool("enable-armor-refresh", "Enable armor refresh with this pathfinder.", true);
    private final Setting<Boolean> helperInventory = bool("enable-inventory-refresh", "Enable the inventory refresh module with this pathfinder.", true);
    private final Setting<Boolean> helperPlacer = bool("enable-sleepy-moss-placer", "Enable Sleepy's moss-placer with this pathfinder when installed.", true);
    private final RegionSettings region = new RegionSettings(settings);
    private final Set<String> ownedHelpers = new LinkedHashSet<>();
    private final Map<String, Boolean> helperChoices = new HashMap<>();
    private RegionBounds lastBounds;
    private long lastRestart;
    private int lastOrder;
    private BlockPos entryTarget;
    private long entrySince;
    private boolean outsideNotified;
    private final SurfaceSweep sweep = new SurfaceSweep();
    private int peakY = Integer.MIN_VALUE, peakCount;
    private double peakX, peakZ;
    private int lastHorizontal;
    private boolean outerSweepReady, lastContours;
    private int lastBand;

    private record Candidate(BlockPos pos, int neighbors, double scanRank) {
        Candidate(BlockPos pos, int neighbors) { this(pos, neighbors, 0); }
    }
    private final List<Candidate> primary = new ArrayList<>(), outer = new ArrayList<>();
    private final PriorityQueue<Candidate> scanResults = new PriorityQueue<>(Comparator.comparingDouble(Candidate::scanRank).reversed());
    private Vec3d scanOrigin;
    private double scanWeight;
    private final Map<BlockPos, Long> skipped = new HashMap<>();
    private final NavigationBudget<BlockPos> commands = new NavigationBudget<>();
    private IBaritone baritone;
    private ClientWorld world;
    private BlockPos target;
    private long ticks, targetSince, progressAt, lastConsider, emptySince = -1;
    private double closestDistance;
    private int scanWait, width, height, minX, minY, minZ, centerX, centerZ, scanRadius;
    private long scanIndex, scanTotal;
    private boolean scanning, outerScan, ownsGoal, paused;
    private String status = "Idle";

    public LavacastPathfinder() {
        super(Utils9000Addon.CATEGORY, "lavacast-pathfinder", "Visits exposed target-block clusters, with region limits, bounded retries and moss supply management.");
        runInMainMenu = true;
    }
    private Setting<Integer> integer(String name, String description, int value, int min, int max) {
        return general.add(new IntSetting.Builder().name(name).description(description).defaultValue(value)
            .range(min, max).sliderRange(min, Math.min(max, Math.max(value * 4, 20))).build());
    }
    private Setting<Boolean> bool(String name, String description, boolean value) {
        return general.add(new BoolSetting.Builder().name(name).description(description).defaultValue(value).build());
    }
    public boolean pauseCombat() { return combatPause.get(); }
    public boolean pauseEating() { return eatingPause.get(); }
    public int resumeDelay() { return resumeTicks.get(); }
    public boolean allowMossBreaking() { return breakMoss.get(); }
    public boolean allowBlockBreaking() { return breakBlocks.get(); }
    public boolean emergencyMossEscape() { return escapeMoss.get(); }
    public int mossEscapeReachableBlocks() { return escapeSpace.get(); }
    public boolean eatingInventoryRecovery() { return eatingRecovery.get(); }
    private final Setting<Boolean> gappleShuffle = bool("gapple-inventory-refresh", "Once per eating episode, move the selected hotbar golden apple into an empty main-inventory slot and immediately back. Requires Pause for Eating. Supports enchanted golden apples.", false);
    public boolean gappleInventoryRefresh() { return gappleShuffle.get(); }
    public boolean eatingKeepOpen() { return eatingKeepOpen.get(); }
    public int eatingOpenTicks() { return eatingOpenTicks.get(); }
    public void renderRegion(meteordevelopment.meteorclient.events.render.Render3DEvent event) { region.render(event); }
    public boolean supplyPing() { return ping.get(); }
    private final Setting<Boolean> keepShulkers = bool("keep-shulkers-out-of-hotbar", "Store shulkers in the main inventory except during refills. Pause placement while moving them.", true);
    public boolean keepShulkersOutOfHotbar() { return keepShulkers.get(); }
    public boolean dropEmptyShulkers() { return dropEmpty.get(); }
    @Override public WWidget getWidget(GuiTheme theme) { return region.widget(theme); }

    @Override public void onActivate() {
        if (mc.player == null || mc.world == null) { error("Join a world first."); toggle(); return; }
        baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        AutomationContext.acquire(this);
        NavigationTransport.stop();
        PathingSafety.walking(this, breakBlocks.get(), scaffold.get(), breakMoss.get(), region.contours() ? targetBlocks.get() : List.of());
        resetWorld();
        lastBounds = region.snapshot();
        RegionConstraint.bounds = lastBounds;
        helperChoices.clear();
        syncHelpers();
    }
    @Override public void onDeactivate() {
        MossEscape.reset();
        ScaffoldGuard.reset();
        ScaffoldPlacementPause.reset();
        AutomationContext.release(this);
        cancelPath();
        RegionConstraint.bounds = null;
        RegionConstraint.entryCorridor = null;
        EmptyShulkerDropper.reset();
        MiningGuard.release();
        for (String name : new ArrayList<>(ownedHelpers)) {
            Module helper = Modules.get().get(name);
            if (helper != null && helper.isActive()) helper.toggle();
        }
        ownedHelpers.clear(); helperChoices.clear();
        PathingSafety.release(this);
        baritone = null;
        target = null;
        world = null;
        primary.clear(); outer.clear(); scanResults.clear(); skipped.clear();
        scanning = false;
        status = "Idle";
    }
    @EventHandler private void leave(GameLeftEvent event) { if (isActive()) toggle(); }
    private void resetWorld() {
        ScaffoldPlacementPause.reset();
        peakY = Integer.MIN_VALUE; peakCount = 0; peakX = peakZ = 0;
        ScaffoldGuard.reset();
        cancelPath();
        world = mc.world;
        primary.clear(); outer.clear(); scanResults.clear(); skipped.clear();
        commands.reset(); target = null;
        entryTarget = null; outsideNotified = false; RegionConstraint.entryCorridor = null;
        ticks = lastConsider = lastRestart = 0; emptySince = -1;
        scanning = paused = false; scanWait = 0;
        sweep.reset(); outerSweepReady = false;
    }

    @EventHandler(priority = -110) private void tick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.world == null || baritone == null) return;
        if (!mc.player.isAlive()) { toggle(); return; }
        if (world != mc.world) resetWorld();
        syncHelpers();
        RegionBounds bounds = region.snapshot();
        RegionConstraint.bounds = bounds;
        if (!Objects.equals(lastBounds, bounds) || lastOrder != region.orderSign() || lastHorizontal != region.horizontalSignature()
            || lastContours != region.contours() || lastBand != region.bandHeight()) {
            lastBounds = bounds; lastOrder = region.orderSign(); resetTargets();
            lastHorizontal = region.horizontalSignature();
            lastContours = region.contours(); lastBand = region.bandHeight();
            entryTarget = null; outsideNotified = false; RegionConstraint.entryCorridor = null;
        }
        if (MossInventory.countMoss() == 0 && MossInventory.findMossShulker() < 0) {
            MossRefill supply = Modules.get().get(MossRefill.class);
            if (supply == null || !supply.transactionActive()) { AutomationShutdown.noMoss(); return; }
        }
        PathingSafety.walking(this, breakBlocks.get(), scaffold.get(), breakMoss.get(), region.contours() ? targetBlocks.get() : List.of());
        MossRefill refill = Modules.get().get(MossRefill.class);
        boolean outside = bounds != null && !bounds.contains(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        if (AutomationContext.blockedScreen() || ActivityPause.isPaused() || ShulkerHotbarGuard.busy() || MossEscape.busy()
            || (refill != null && refill.shouldPauseWalking() && (!outside || refill.transactionActive()))) {
            if (!paused) pauseForRefill();
            status = ActivityPause.isPaused() ? "Paused for combat/eating" : "Paused for moss refill";
            return;
        }
        if (paused) { paused = false; scanWait = 0; }
        ticks++;
        skipped.entrySet().removeIf(e -> e.getValue() <= ticks);
        if (bounds != null && !bounds.contains(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ())) {
            returnToRegion(bounds); return;
        }
        if (outsideNotified || entryTarget != null) {
            cancelPath(); entryTarget = null; outsideNotified = false; RegionConstraint.entryCorridor = null;
            resetTargets(); info("Back inside the selected region.");
        }
        if (restart.get() && restartSeconds.get() > 0 && ticks - lastRestart >= restartSeconds.get() * 20L) {
            lastRestart = ticks;
            // Move on briefly rather than immediately choosing exactly the same stalled goal.
            abandon(Math.max(1, restartSeconds.get()));
        }
        skipped.entrySet().removeIf(e -> e.getValue() <= ticks);
        if (target != null) checkTarget();

        if (scanning) scanBatch();
        else if (scanWait-- <= 0) { beginScan(false); scanBatch(); }

        if (target == null || (!region.contours() && ticks - lastConsider >= reconsider.get())) {
            lastConsider = ticks;
            Candidate next = chooseTarget();
            if (next != null && (target == null || shouldSwitch(next))) dispatch(next.pos);
        }
    }

    private void checkTarget() {
        if (!eligible(target) || neighborCount(target) < neighbors.get()) { abandon(0); return; }
        if (mc.player.isOnGround() && baritone.getPlayerContext().playerFeet().equals(goalFor(target))) { abandon(revisit.get()); return; }
        double distance = distance(target);
        if (distance <= closestDistance - .75) { closestDistance = distance; progressAt = ticks; }
        boolean exhausted = maxAttempt.get() > 0 && ticks - targetSince >= maxAttempt.get() * 20L;
        boolean stalled = stall.get() > 0 && ticks - progressAt >= stall.get() * 20L;
        boolean failed = ticks - targetSince >= Math.max(10, goalDelay.get())
            && !baritone.getCustomGoalProcess().isActive()
            && baritone.getPathingBehavior().getInProgress().isEmpty()
            && !baritone.getPathingBehavior().isPathing();
        if (exhausted || stalled || failed || tooIndirect()) abandon(failedDelay.get());
    }

    private boolean tooIndirect() {
        if (region.contours() || detourRatio.get() <= 0 || ticks - targetSince < 5) return false;
        var executor = baritone.getPathingBehavior().getCurrent();
        if (executor == null || !executor.getPath().getGoal().isInGoal(goalFor(target))) return false;
        var positions = executor.getPath().positions();
        int from = Math.min(executor.getPosition(), positions.size() - 1);
        if (from < 0) return false;
        Vec3d previous = mc.player.getPos();
        double length = 0;
        for (int i = from; i < positions.size(); i++) {
            Vec3d point = Vec3d.ofBottomCenter(positions.get(i));
            length += previous.distanceTo(point);
            previous = point;
        }
        length += previous.distanceTo(Vec3d.ofBottomCenter(goalFor(target)));
        return length > Math.max(12, distance(target) * detourRatio.get());
    }

    private void abandon(int cooldownSeconds) {
        if (target != null && cooldownSeconds > 0) skipped.put(target, ticks + cooldownSeconds * 20L);
        cancelPath();
        target = null;
        scanWait = 0;
    }
    private void dispatch(BlockPos pos) {
        pos = advanceHorizontalGoal(pos);
        if (!commands.canDispatch(ticks, pos, goalDelay.get())) return;
        // Do not toggle the module: preserve failed-target memory and settings ownership.
        if (region.contours() && sweep.active()) {
            double cx = sweep.centerX(), cz = sweep.centerZ();
            double fromRadius = Math.hypot(mc.player.getX() - cx, mc.player.getZ() - cz);
            double toRadius = Math.hypot(pos.getX() + .5 - cx, pos.getZ() + .5 - cz);
            ContourRoute.current = new ContourRoute(cx, cz, Math.max(0, Math.min(fromRadius, toRadius) - 2),
                Math.max(mc.player.getBlockY(), goalFor(pos).getY()) + 1);
        } else ContourRoute.current = null;
        NavigationTransport.go(goalFor(pos));
        if (region.contours()) sweep.selected(pos.getX() + .5, pos.getZ() + .5);
        commands.dispatched(ticks, pos);
        ownsGoal = true; target = pos;
        targetSince = progressAt = ticks;
        closestDistance = distance(pos);
        status = (within(pos, radius.get()) ? "Local " : "Expanded ") + pos.toShortString();
    }
    private boolean shouldSwitch(Candidate next) {
        if (region.horizontal()) return false; // Finish this route rather than bounce between rings.
        if (next.pos.equals(target)) return false;
        if (region.orderSign() != 0 && next.pos.getY() != target.getY())
            return region.orderSign() * Integer.compare(next.pos.getY(), target.getY()) < 0;
        if (within(next.pos, radius.get()) && !within(target, radius.get())) return true;
        double current = TargetRules.score(distance(target), neighborCount(target), clusterWeight.get());
        return score(next) < current * (1 - improvement.get() / 100.0);
    }
    /** Step into the fresh side of a moss boundary rather than stopping directly on its rim. */
    private BlockPos advanceHorizontalGoal(BlockPos boundary) {
        int advance = region.goalAdvance();
        if (advance == 0) return boundary;
        double mossX = 0, mossZ = 0; int count = 0;
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            var cover = boundary.add(x, 1, z);
            if (world.isChunkLoaded(cover.getX() >> 4, cover.getZ() >> 4) && world.getBlockState(cover).isOf(Blocks.MOSS_BLOCK)) {
                mossX += x; mossZ += z; count++;
            }
        }
        if (count == 0) return boundary;
        double length = Math.hypot(mossX, mossZ);
        if (length < .01) return boundary;
        BlockPos best = boundary;
        for (int step = 1; step <= advance; step++) {
            BlockPos next = boundary.add((int) Math.round(-mossX / length * step), 0, (int) Math.round(-mossZ / length * step));
            if (skipped.containsKey(next) || !eligible(next) || neighborCount(next) < neighbors.get()
                || !within(next, Math.max(radius.get(), fallbackRadius.get()))) break;
            best = next;
        }
        return best;
    }
    private Candidate chooseTarget() {
        if (region.contours() && !sweep.active()) {
            Candidate seed = layerCandidate(false, false);
            if (seed != null) beginSweep(seed);
        }
        Candidate local = nearest(primary, false);
        Candidate rediscovered = nearest(outer, false);
        if (rediscovered != null && (local == null || better(rediscovered, local))) local = rediscovered;
        if (local != null) { emptySince = -1; return local; }
        if (emptySince < 0) emptySince = ticks;
        if (!TargetRules.fallbackReady(ticks, emptySince, fallbackDelay.get() * 20)) return null;
        if (region.contours() && !sweep.active() && outerSweepReady) {
            Candidate seed = layerCandidate(true, false);
            if (seed != null) { beginSweep(seed); return seed; }
        }
        Candidate expanded = nearest(outer, true);
        if (expanded != null) return expanded;
        if (region.contours() && outerSweepReady) {
            // Only advance after a completed expanded scan finds no work in the current band.
            Candidate seed = layerCandidate(true, true);
            if (seed == null) seed = layerCandidate(true, false);
            if (seed != null) { beginSweep(seed); return seed; }
        }
        return null;
    }
    private Candidate nearest(List<Candidate> candidates, boolean expanded) {
        Candidate best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int range = expanded ? Math.max(radius.get(), fallbackRadius.get()) : radius.get();
        for (Candidate candidate : candidates) {
            BlockPos pos = candidate.pos;
            if (skipped.containsKey(pos) || !within(pos, range) || Math.abs(pos.getY() - mc.player.getY()) > vertical.get()
                || baritone.getPlayerContext().playerFeet().equals(goalFor(pos)) || !eligible(pos)
                || (region.contours() && !sweep.accepts(pos.getY()))) continue;
            int count = neighborCount(pos);
            if (count < neighbors.get()) continue;
            Candidate checked = new Candidate(pos, count);
            double score = score(checked);
            if (best == null || better(checked, best)) { best = checked; bestScore = score; }
        }
        return best;
    }
    private double score(Candidate c) {
        double base = TargetRules.score(distance(c.pos), c.neighbors, clusterWeight.get());
        return region.contours() ? sweep.rank(c.pos.getX() + .5, c.pos.getZ() + .5, base) : base;
    }
    private boolean better(Candidate next, Candidate old) {
        if (!region.contours() && region.orderSign() != 0 && next.pos.getY() != old.pos.getY())
            return region.orderSign() * Integer.compare(next.pos.getY(), old.pos.getY()) < 0;
        double nextRing = region.horizontalRank(next.pos.getX(), next.pos.getZ());
        double oldRing = region.horizontalRank(old.pos.getX(), old.pos.getZ());
        if (nextRing != oldRing) return nextRing < oldRing;
        return score(next) < score(old);
    }
    private double distance(BlockPos pos) { return mc.player.getPos().distanceTo(Vec3d.ofBottomCenter(goalFor(pos))); }
    private BlockPos goalFor(BlockPos pos) {
        var cover = world.getBlockState(pos.up());
        return snow.get() && cover.isOf(Blocks.SNOW) && cover.get(SnowBlock.LAYERS) == 8 ? pos.up(2) : pos.up();
    }
    private boolean within(BlockPos pos, int range) {
        double dx = pos.getX() + .5 - mc.player.getX(), dz = pos.getZ() + .5 - mc.player.getZ();
        return dx * dx + dz * dz <= (double) range * range;
    }

    private void beginScan(boolean expanded) {
        BlockPos origin = mc.player.getBlockPos();
        scanOrigin = mc.player.getPos();
        scanWeight = clusterWeight.get();
        outerScan = expanded;
        scanRadius = expanded ? Math.max(radius.get(), fallbackRadius.get()) : radius.get();
        centerX = origin.getX(); centerZ = origin.getZ();
        minX = centerX - scanRadius; minZ = centerZ - scanRadius;
        minY = Math.max(world.getBottomY(), origin.getY() - vertical.get());
        int maxY = Math.min(world.getTopYInclusive() - Math.max(1, air.get()), origin.getY() + vertical.get());
        width = 2 * scanRadius + 1; height = Math.max(0, maxY - minY + 1);
        scanIndex = 0; scanTotal = (long) width * width * height;
        scanResults.clear(); scanning = true;
        if (target == null) status = expanded ? "Scanning expanded radius" : "Scanning local radius";
    }
    private void scanBatch() {
        int budget = scanBudget.get();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        while (scanIndex < scanTotal && budget-- > 0) {
            long column = scanIndex / height;
            int x = minX + (int) (column % width), z = minZ + (int) (column / width);
            int dx = x - centerX, dz = z - centerZ;
            if (dx * dx + dz * dz > scanRadius * scanRadius || !world.isChunkLoaded(x >> 4, z >> 4)) {
                scanIndex = (column + 1) * height;
                continue;
            }
            cursor.set(x, minY + (int) (scanIndex % height), z);
            scanIndex++;
            if (region.contours() && (lastBounds == null || lastBounds.contains(x, cursor.getY() + 1, z))
                && targetBlocks.get().contains(world.getBlockState(cursor).getBlock())) {
                var cover = world.getBlockState(cursor.up());
                if (cover.isAir() || cover.isOf(Blocks.MOSS_BLOCK) || cover.isOf(Blocks.SNOW)) {
                    // The cone's highest visible surface estimates its axis even after the peak is mossed.
                    if (cursor.getY() > peakY) { peakY = cursor.getY(); peakCount = 0; peakX = peakZ = 0; }
                    if (cursor.getY() == peakY && peakCount < 1_000_000) { peakCount++; peakX += x + .5; peakZ += z + .5; }
                }
            }
            if (skipped.containsKey(cursor) || !eligible(cursor) || baritone.getPlayerContext().playerFeet().equals(goalFor(cursor))) continue;
            int count = neighborCount(cursor);
            if (count >= neighbors.get()) {
                double rank = region.orderSign() * cursor.getY() * 1_000_000.0
                    + TargetRules.score(scanOrigin.distanceTo(Vec3d.ofBottomCenter(cursor.up())), count, scanWeight);
                if (region.contours() && sweep.active()) rank = (sweep.accepts(cursor.getY()) ? 0 : 1_000_000_000.0)
                    + sweep.rank(cursor.getX() + .5, cursor.getZ() + .5, TargetRules.score(scanOrigin.distanceTo(Vec3d.ofBottomCenter(cursor.up())), count, scanWeight));
                rank += region.horizontalRank(cursor.getX(), cursor.getZ()) * 100_000;
                Candidate candidate = new Candidate(cursor.toImmutable(), count, rank);
                // Keep memory and selection work bounded even with a 512-block fallback radius.
                if (scanResults.size() < 4096) scanResults.add(candidate);
                else if (rank < scanResults.peek().scanRank) { scanResults.poll(); scanResults.add(candidate); }
            }
        }
        if (scanIndex < scanTotal) return;
        scanning = false;
        List<Candidate> cache = outerScan ? outer : primary;
        cache.clear(); cache.addAll(scanResults);
        if (outerScan) outerSweepReady = true;
        if (!outerScan) {
            Candidate local = nearest(primary, false);
            if (local != null) emptySince = -1;
            else if (emptySince < 0) emptySince = ticks;
            if (local == null && TargetRules.fallbackReady(ticks, emptySince, fallbackDelay.get() * 20)) {
                beginScan(true);
                return;
            }
        }
        scanWait = refresh.get();
    }
    private boolean eligible(BlockPos pos) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) || !world.getWorldBorder().contains(pos)
            || (lastBounds != null && !lastBounds.contains(pos.getX(), goalFor(pos).getY(), pos.getZ()))
            || !targetBlocks.get().contains(world.getBlockState(pos).getBlock())) return false;
        BlockPos.Mutable above = new BlockPos.Mutable();
        return TargetRules.hasClearance(pos.getY(), world.getTopYInclusive() + 1, air.get(),
            y -> {
                var state = world.getBlockState(above.set(pos.getX(), y, pos.getZ()));
                return TargetRules.permitsCover(y - pos.getY(), state.isAir(), state.isOf(Blocks.SNOW), snow.get());
            });
    }
    private int neighborCount(BlockPos pos) {
        BlockPos.Mutable neighbor = new BlockPos.Mutable();
        return TargetRules.countNeighbors((x, y, z) -> {
            neighbor.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
            return world.isChunkLoaded(neighbor.getX() >> 4, neighbor.getZ() >> 4)
                && targetBlocks.get().contains(world.getBlockState(neighbor).getBlock());
        });
    }
    public void pauseForRefill() {
        cancelPath(); target = null; entryTarget = null; scanning = false; paused = true;
    }
    private void cancelPath() {
        ContourRoute.current = null;
        if (baritone != null && ownsGoal) NavigationTransport.stop();
        ownsGoal = false; commands.clear();
    }
    @Override public String getInfoString() { return status; }

    /** Invalidate cached selections immediately after a whitelist or region edit. */
    private void resetTargets() {
        peakY = Integer.MIN_VALUE; peakCount = 0; peakX = peakZ = 0;
        cancelPath(); target = null; scanning = false; scanWait = 0; emptySince = -1;
        if (primary != null) { primary.clear(); outer.clear(); scanResults.clear(); skipped.clear(); }
        if (sweep != null) sweep.reset();
        outerSweepReady = false;
    }

    private Candidate layerCandidate(boolean expanded, boolean ahead) {
        Candidate best = null;
        int range = expanded ? Math.max(radius.get(), fallbackRadius.get()) : radius.get();
        for (List<Candidate> cache : List.of(primary, outer)) for (Candidate candidate : cache) {
            BlockPos pos = candidate.pos;
            if (skipped.containsKey(pos) || !within(pos, range) || Math.abs(pos.getY() - mc.player.getY()) > vertical.get()
                || !eligible(pos) || baritone.getPlayerContext().playerFeet().equals(goalFor(pos))
                || (ahead && !sweep.ahead(pos.getY()))) continue;
            int count = neighborCount(pos);
            if (count < neighbors.get()) continue;
            if (best == null || region.orderSign() * Integer.compare(pos.getY(), best.pos.getY()) < 0
                || (pos.getY() == best.pos.getY() && better(new Candidate(pos, count), best))) best = new Candidate(pos, count);
        }
        return best;
    }
    private void beginSweep(Candidate seed) {
        int anchor = seed.pos.getY(), band = region.bandHeight(), sign = region.orderSign();
        Set<BlockPos> positions = new HashSet<>();
        for (List<Candidate> cache : List.of(primary, outer)) for (Candidate c : cache) {
            int delta = (c.pos.getY() - anchor) * sign;
            if (delta >= 0 && delta < band && within(c.pos, Math.max(radius.get(), fallbackRadius.get()))) positions.add(c.pos);
        }
        positions.add(seed.pos);
        double x = 0, z = 0;
        for (BlockPos pos : positions) { x += pos.getX() + .5; z += pos.getZ() + .5; }
        sweep.begin(anchor, sign, band, peakCount > 0 ? peakX / peakCount : x / positions.size(),
            peakCount > 0 ? peakZ / peakCount : z / positions.size(), mc.player.getX(), mc.player.getZ());
        outerSweepReady = false; emptySince = -1;
    }
    private void syncHelpers() {
        configureHelper("moss-shulker-refill", helperRefill.get());
        configureHelper("moss-armor-refresh", helperArmor.get());
        configureHelper("moss-inventory-refresh", helperInventory.get());
        configureHelper("moss-placer", helperPlacer.get());
    }
    private void configureHelper(String name, boolean wanted) {
        Boolean previous = helperChoices.put(name, wanted);
        if (previous != null && previous == wanted) return;
        Module helper = Modules.get().get(name);
        if (helper == null) { if (wanted) warning("Optional module %s is not installed.", name); return; }
        if (wanted && !helper.isActive()) { helper.toggle(); ownedHelpers.add(name); }
        else if (!wanted && previous != null && helper.isActive()) { helper.toggle(); ownedHelpers.remove(name); }
    }

    /** Allow a bounded entry corridor only while approaching an otherwise strict region. */
    private void returnToRegion(RegionBounds bounds) {
        BlockPos feet = mc.player.getBlockPos();
        int reach = Math.max(radius.get(), fallbackRadius.get());
        boolean reachableRange = bounds.distanceSquared(feet.getX(), feet.getY(), feet.getZ()) <= (double) reach * reach;
        if (!outsideNotified) {
            warning("Outside selected region. %s", region.returnInside() && reachableRange
                ? "Finding a route back inside." : "Region is out of scan range or automatic return is disabled.");
            outsideNotified = true;
        }
        if (!region.returnInside() || !reachableRange) { cancelPath(); RegionConstraint.entryCorridor = null; status = "Outside region"; return; }
        if (entryTarget != null) {
            if (ticks - entrySince < 200 && (ownsGoal && (baritone.getPathingBehavior().isPathing()
                || baritone.getPathingBehavior().getInProgress().isPresent() || ticks - entrySince < 20))) {
                status = "Returning to region"; return;
            }
            skipped.put(entryTarget, ticks + 1200); cancelPath(); entryTarget = null;
        }
        BlockPos center = new BlockPos(bounds.clampX(feet.getX()), bounds.clampY(feet.getY()), bounds.clampZ(feet.getZ()));
        BlockPos best = null; double bestScore = Double.POSITIVE_INFINITY;
        for (int x = -8; x <= 8; x++) for (int y = -12; y <= 12; y++) for (int z = -8; z <= 8; z++) {
            BlockPos p = center.add(x, y, z);
            if (skipped.containsKey(p) || !bounds.contains(p.getX(), p.getY(), p.getZ()) || !world.isChunkLoaded(p)
                || !world.getWorldBorder().contains(p) || !world.getBlockState(p).isAir() || !world.getBlockState(p.up()).isAir()) continue;
            double distance = p.getSquaredDistance(feet);
            if (distance > (double) reach * reach) continue;
            boolean floor = world.getBlockState(p.down()).isSideSolidFullSquare(world, p.down(), net.minecraft.util.math.Direction.UP);
            if (!floor && !scaffold.get()) continue;
            double rank = distance + (floor ? 0 : 10000);
            if (rank < bestScore) { best = p; bestScore = rank; }
        }
        if (best == null) { status = "No loaded entry point"; return; }
        if (!commands.canDispatch(ticks, best, Math.max(20, goalDelay.get()))) return;
        RegionConstraint.entryCorridor = RegionBounds.normalized(
            Math.min(feet.getX(), best.getX()) - 8, Math.max(world.getBottomY(), Math.min(feet.getY(), best.getY()) - 8), Math.min(feet.getZ(), best.getZ()) - 8,
            Math.max(feet.getX(), best.getX()) + 8, Math.min(world.getTopYInclusive(), Math.max(feet.getY(), best.getY()) + 8), Math.max(feet.getZ(), best.getZ()) + 8);
        ContourRoute.current = null;
        NavigationTransport.go(best); commands.dispatched(ticks, best);
        entryTarget = best; entrySince = ticks; ownsGoal = true; status = "Returning to region";
    }
}
