package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Opportunistic coverage planner: the walker owns travel; this module owns short placement transactions. */
public final class RoofLighting extends Module {
    private final DebugView debug = new DebugView(this,settings,
        () -> isActive() ? getInfoString() : "Preview only (lighting off)",this::drawDebug,true);
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<List<Block>> supports = general.add(new BlockListSetting.Builder().name("placement-support-blocks")
        .description("Only place lights directly on these blocks. Defaults to moss; an empty list disables light placement.")
        .defaultValue(Blocks.MOSS_BLOCK).build());
    private final Setting<Integer> interval = general.add(new IntSetting.Builder().name("scan-interval-ticks").description("Ticks between local roof scans.").defaultValue(5).range(5, 1200).sliderRange(5, 100).build());
    private final Setting<Integer> radius = general.add(new IntSetting.Builder().name("coverage-scan-radius").description("Loaded roof radius scored around the player; Short approaches visit eligible moss-supported grid sites within six blocks.").defaultValue(16).range(4, 24).sliderRange(4, 24).build());
    private final Setting<Boolean> retryMissed = general.add(new BoolSetting.Builder().name("retry-missed-positions")
        .description("Visit missing moss-supported grid lights, then return along the saved trail or a shorter reachable route to moss work.").defaultValue(true).build());
    private final Setting<Integer> retryRange = general.add(new IntSetting.Builder().name("retry-range")
        .description("Radius in blocks for missing grid lights. Only loaded eligible moss supports are visited.")
        .defaultValue(32).range(1,128).sliderRange(1,64).visible(retryMissed::get).build());
    private final Setting<Integer> minimum = general.add(new IntSetting.Builder().name("minimum-block-light").description("Required BLOCK light at surface air cells. Skylight is deliberately ignored, including during daytime.").defaultValue(1).range(1, 15).sliderRange(1, 15).build());
    private final SettingGroup gridGroup = settings.createGroup("Fixed Grid");
    private final Setting<Boolean> fixedGrid = gridGroup.add(new BoolSetting.Builder().name("fixed-grid-placement")
        .description("Use only planned grid coordinates unless the separate Roof Light Gap Filler module is enabled.").defaultValue(true).build());
    private final Setting<Integer> overlap = gridGroup.add(new IntSetting.Builder().name("grid-overlap-margin")
        .description("Extra block-light headroom in the flat-roof layout. Default 1 gives radius 13 and minimum theoretical light 2 with level-15 sources.")
        .defaultValue(1).range(0,10).sliderRange(0,5).build());
    private final Setting<Integer> fromX = coordinate("grid-min-x",-5000);
    private final Setting<Integer> toX = coordinate("grid-max-x",5000);
    private final Setting<Integer> fromZ = coordinate("grid-min-z",-5000);
    private final Setting<Integer> toZ = coordinate("grid-max-z",5000);
    private Setting<Integer> coordinate(String name,int value) {
        return gridGroup.add(new IntSetting.Builder().name(name).description("Inclusive project boundary; grid origin is world 0,0. Edge positions are moved onto this boundary.")
            .defaultValue(value).range(-30000000,30000000).sliderRange(-5000,5000).build());
    }
    private LightGrid grid() { return new LightGrid(fromX.get(),toX.get(),fromZ.get(),toZ.get(),LightGrid.radius(15,minimum.get(),overlap.get())); }
    private static RoofGapFiller gapFiller() { return Modules.get().get(RoofGapFiller.class); }
    static int requiredSupplyStrength() {
        var roof=Modules.get().get(RoofLighting.class); var gaps=gapFiller();
        return roof!=null && roof.isActive() && roof.fixedGrid.get() && (gaps==null || !gaps.isActive()) ? 15 : 1;
    }
    private record GridMarker(BlockPos pos,boolean complete,boolean ready) { }
    private List<GridMarker> gridMarkers=List.of();
    private long nextMarkers;
    private net.minecraft.client.world.ClientWorld markerWorld;
    private final Setting<Boolean> refill = general.add(new BoolSetting.Builder().name("auto-refill").description("Enable Light Shulker Refill while lighting. Requires supply boxes in your inventory.").defaultValue(true).build());
    private final Setting<Boolean> protect = general.add(new BoolSetting.Builder().name("protect-light-sources")
        .description("Reject moss placements against any face of a light-emitting block, including Sleepy's direct packets.").defaultValue(true).build());
    private final Setting<Boolean> pumpkinSound = general.add(new BoolSetting.Builder().name("pumpkin-sound")
        .description("Play the supplied PUMPKIN! sound after a confirmed jack o'lantern placement.").defaultValue(false).build());
    private final Setting<Double> pumpkinVolume = general.add(new DoubleSetting.Builder().name("pumpkin-volume")
        .description("Sound volume, also affected by Minecraft's master volume.").defaultValue(0.5).range(0, 1).sliderRange(0, 1).visible(pumpkinSound::get).build());
    public boolean protectLightSources() { return protect.get(); }
    private enum Phase { IDLE, SELECT, APPROACH, PLACE, CONFIRM, RESTORE, RETURN_SEARCH, RETURN_WALK }
    private Phase phase = Phase.IDLE;
    private int clock, wait, age, slot, previous = -1;
    private boolean ownedRefill;
    private volatile boolean confirmed;
    private BlockPos target, approachGoal;
    private boolean navigationOwned, approachSent;
    private int approachTicks, placementAttempts, confirmTicks, retryJourneyCount, returnAge, returnStall;
    private boolean retryJourney, returning, returnFallback;
    private final List<BlockPos> departureTrail=new ArrayList<>();
    private final Deque<BlockPos> returnRoute=new ArrayDeque<>();
    private BoundedRouteSearch<BlockPos> returnSearch;
    private BlockPos returnGoal, returnOrigin;
    private double returnBest;
    private boolean waitingForPlayer;
    private ItemStack expected, displaced;
    private int storageSlot = -1;
    private net.minecraft.client.world.ClientWorld world;
    private final Map<BlockPos, Integer> rejected = new HashMap<>();
    private String status = "Scanning";

    public RoofLighting() { super(Utils9000Addon.CATEGORY, "roof-lighting", "Places inventory light blocks to cover dark roof surfaces while the pathfinder travels. Works in daylight."); }
    static boolean placing() {
        var roof = Modules.get().get(RoofLighting.class);
        return roof != null && roof.isActive() && roof.phase != Phase.IDLE;
    }
    static boolean busy() {
        var supply = Modules.get().get(LightRefill.class);
        return placing() || (supply != null && supply.transactionActive());
    }
    @Override public void onActivate() {
        AutomationContext.acquire(this); PathingSafety.protectLights(true); world = mc.world; phase = Phase.IDLE; wait = 0; rejected.clear(); gridMarkers=List.of(); nextMarkers=0;
    }
    @Override public void onDeactivate() {
        var gaps=gapFiller();
        if (gaps!=null) gaps.stopWithRoof();
        release(); PathingSafety.protectLights(false); AutomationContext.release(this);
        var supply = Modules.get().get(LightRefill.class);
        if (ownedRefill && supply != null && supply.isActive()) supply.toggle();
        ownedRefill = false;
    }
    @EventHandler private void receive(meteordevelopment.meteorclient.events.packets.PacketEvent.Receive event) {
        if (event.packet instanceof net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket packet) observe(packet.getPos(), packet.getState());
        if (event.packet instanceof net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket packet) packet.visitUpdates(this::observe);
    }
    private void observe(BlockPos pos, net.minecraft.block.BlockState state) {
        if (phase == Phase.CONFIRM && pos.equals(target) && state.getLuminance() >= minimum.get()) confirmed = true;
    }
    @EventHandler private void leave(GameLeftEvent event) { if (isActive()) toggle(); }
    @EventHandler(priority = -105) private void tick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (world != mc.world || !mc.player.isAlive()) { toggle(); return; }
        var supply = Modules.get().get(LightRefill.class);
        if (refill.get() && supply != null && !supply.isActive() && !ownedRefill) { supply.toggle(); ownedRefill = true; }
        if (!refill.get() && ownedRefill) { if (supply != null && supply.isActive()) supply.toggle(); ownedRefill = false; }
        if (phase != Phase.IDLE) InventoryGuard.maintain(this);
        if (ActivityPause.isPaused() || AutomationContext.blockedScreen()) {
            if(navigationOwned) { NavigationTransport.stop(); approachSent=false; }
            return;
        }
        clock++;
        if(phase==Phase.APPROACH || phase==Phase.PLACE) {
            waitingForPlayer=blockers(target).stream().anyMatch(e->e instanceof net.minecraft.entity.player.PlayerEntity);
            if(waitingForPlayer) {
                if(navigationOwned && approachSent)NavigationTransport.stop();
                approachSent=false;status="Waiting for player to leave light position";return;
            }
        }
        if(retryJourney && !returning && mc.player.isOnGround())recordDeparture();
        if(phase==Phase.RETURN_SEARCH) { findReturnRoute();return; }
        if(phase==Phase.RETURN_WALK) { walkReturn();return; }
        if (phase != Phase.IDLE) {
            if (!waitingForPlayer && ++age > (retryJourney ? 400 : 160)) { reject("Placement timed out; keeping the site eligible for later retry"); return; }
            if (wait-- > 0) return;
            switch (phase) {
                case SELECT -> {
                    if (!MossInventory.playerInventoryReady()) return;
                    if (!ItemStack.areEqual(expected, mc.player.getInventory().getStack(slot))) { reject("Hotbar transfer changed"); return; }
                    InvUtils.swap(slot, false); phase = approachGoal==null ? Phase.PLACE : Phase.APPROACH; wait = 2;
                }
                case APPROACH -> approach();
                case PLACE -> place();
                case CONFIRM -> {
                    confirmTicks++;
                    // Creative placement consumes no items; the server block update still confirms success.
                    if (confirmed && mc.world.getBlockState(target).getLuminance() >= minimum.get()
                        && (mc.player.getAbilities().creativeMode
                            || mc.player.getInventory().getStack(slot).getCount() < expected.getCount())) {
                        // Play once, only after the jack o'lantern placement has been confirmed.
                        if (pumpkinSound.get() && expected.isOf(net.minecraft.item.Items.JACK_O_LANTERN)
                            && mc.world.getBlockState(target).isOf(Blocks.JACK_O_LANTERN)) {
                            mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                                net.minecraft.sound.SoundEvent.of(net.minecraft.util.Identifier.of("utils9000", "pumpkin")), 1f, pumpkinVolume.get().floatValue()));
                        }
                        // Give the client light engine time to propagate before another scan.
                        if(navigationOwned) { NavigationTransport.stop(); navigationOwned=false; }
                        restoreStack(); phase = Phase.RESTORE; wait = 4; status = "Light placed";
                    } else if(confirmTicks>=20) retryPlacement("No server placement confirmation");
                }
                case RESTORE -> finishPlacement();
                default -> { }
            }
            return;
        }
        if (wait-- > 0) return;
        wait = interval.get();
        var moss = Modules.get().get(MossRefill.class);
        if (MossEscape.busy() || ShulkerHotbarGuard.busy() || (moss != null && moss.transactionActive())
            || (supply != null && supply.transactionActive()) || !MossInventory.playerInventoryReady() || !mc.player.isOnGround()) return;
        if (!RegionConstraint.allows(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ())) return;
        rejected.entrySet().removeIf(e -> e.getValue() <= clock);
        scan();
    }

    private void scan() {
        Candidate retry=findRetryCandidate();
        if(retry!=null) { beginCandidate(retry,true);return; }
        // One representative per light strength/block: the strongest valid placement wins coverage ties.
        List<BlockPos> dark = new ArrayList<>();
        BlockPos origin = mc.player.getBlockPos();
        int r = radius.get();
        LightGrid layout=grid();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) for (int y = -2; y <= 2; y++) {
            BlockPos pos = origin.add(x, y, z);
            if (layout.inside(pos.getX(),pos.getZ()) && surface(pos) && mc.world.getLightLevel(LightType.BLOCK, pos) < minimum.get()) dark.add(pos);
        }
        if (dark.isEmpty()) { status = "Nearby surfaces lit"; return; }
        Candidate best = fixedGrid.get() ? choose(dark,true,1) : null;
        var gaps=gapFiller();
        if (best==null && gaps!=null && gaps.isActive()) best=choose(dark,false,gaps.minimumNewlyLit());
        if (best == null) {
            int hotbarLight=LightInventory.hotbarLight();
            status = hotbarLight>=0 && LightInventory.strength(mc.player.getInventory().getStack(hotbarLight))<requiredSupplyStrength()
                ? "Hotbar light is too weak for this grid; move it manually to use stronger supplies"
                : LightInventory.count()==0 ? "Waiting for compatible light supplies"
                : fixedGrid.get() ? "Waiting for reachable grid position" : "Grid off; enable Roof Light Gap Filler for cleanup";
            return;
        }
        beginCandidate(best,false);
    }
    private void beginCandidate(Candidate best,boolean retryTrip) {
        if (!InventoryGuard.acquire(this)) return;
        if(retryTrip && !retryJourney) {
            retryJourney=true;returning=false;retryJourneyCount=0;departureTrail.clear();recordDeparture();
        }
        if(previous<0) previous = mc.player.getInventory().selectedSlot;
        target = best.pos; approachGoal=best.approach; approachSent=false; approachTicks=0; expected = mc.player.getInventory().getStack(best.source).copy();
        slot = best.source < 9 ? best.source : MossInventory.safeHotbarSlot(previous);
        if (slot < 0) { reject("No unprotected hotbar slot available"); return; }
        if (best.source >= 9) {
            if (LightInventory.hotbarLight()>=0) { reject("Use existing hotbar lights first"); return; }
            storageSlot = best.source;
            displaced = mc.player.getInventory().getStack(slot).copy();
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, MossInventory.playerSlotId(best.source), slot, SlotActionType.SWAP, mc.player);
        }
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (walker != null && walker.isActive()) walker.pauseForRefill();
        NavigationTransport.stop(); PathingSafety.acquire(this);
        waitingForPlayer=false; placementAttempts=0; confirmTicks=0; phase = Phase.SELECT; age = 0; wait = 4; status = "Placing light";
    }
    /** Grid sites have priority; opportunistic candidates exist only with the cleanup module active. */
    private Candidate choose(List<BlockPos> dark,boolean gridOnly,int threshold) {
        BlockPos origin=mc.player.getBlockPos();
        LightGrid layout=grid();
        Set<LightGrid.Site> planned=gridOnly ? layout.sites(origin.getX()-6,origin.getX()+6,origin.getZ()-6,origin.getZ()+6) : Set.of();
        List<Candidate> candidates = new ArrayList<>();
        Set<net.minecraft.block.Block> seen = new HashSet<>();
        int existingLight=LightInventory.hotbarLight();
        for (int source = 0; source < 36; source++) {
            if (existingLight>=0 && source!=existingLight) continue;
            ItemStack stack = mc.player.getInventory().getStack(source);
            int strength = LightInventory.strength(stack);
            if ((gridOnly && strength<15) || strength < minimum.get() || !(stack.getItem() instanceof BlockItem item) || !seen.add(item.getBlock())) continue;
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) for (int y = -2; y <= 2; y++) {
                BlockPos pos = origin.add(x, y, z);
                if (!layout.inside(pos.getX(),pos.getZ()) || (gridOnly && !planned.contains(new LightGrid.Site(pos.getX(),pos.getZ())))) continue;
                if (rejected.containsKey(pos) || !surface(pos) || !clear(pos) || !item.getBlock().getDefaultState().canPlaceAt(mc.world, pos)) continue;
                boolean inReach=visible(hit(pos));
                BlockPos approach=gridOnly && LightApproachRules.nearby(mc.player.getX(),mc.player.getY(),mc.player.getZ(),pos.getX()+.5,pos.getY(),pos.getZ()+.5)
                    && mc.world.getBlockState(pos.down()).isOf(Blocks.MOSS_BLOCK) ? approachPosition(pos) : null;
                if (!inReach && approach==null) continue;
                int score = 0;
                for (BlockPos cell : dark) if (LightCoverage.lightAt(strength, cell.getX()-pos.getX(), cell.getY()-pos.getY(), cell.getZ()-pos.getZ()) >= minimum.get()) score++;
                if (score >= threshold) candidates.add(new Candidate(pos, source, strength, score, approach));
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::score).reversed());
        Candidate best = null; int bestScore = 0;
        // Bound expensive obstacle-aware propagation to the best twelve geometric candidates.
        for (int i = 0; i < Math.min(12, candidates.size()); i++) {
            Candidate c = candidates.get(i);
            int score = reachableCoverage(c, dark);
            if (score >= threshold && score > bestScore) { best = c; bestScore = score; }
        }
        return best;
    }
    private record Candidate(BlockPos pos, int source, int strength, int score, BlockPos approach) { }

    /** Conservative flood fill through air. Real world light is checked again after each placement. */
    private int reachableCoverage(Candidate candidate, List<BlockPos> dark) {
        Map<BlockPos, Integer> light = LightCoverage.propagate(candidate.pos, candidate.strength, minimum.get(),
            pos -> List.of(pos.up(), pos.down(), pos.north(), pos.south(), pos.east(), pos.west()),
            pos -> mc.world.isChunkLoaded(pos) && mc.world.getBlockState(pos).isAir());
        int score = 0;
        for (BlockPos pos : dark) if (light.getOrDefault(pos, 0) >= minimum.get()) score++;
        return score;
    }
    /** A close dry standing position with line of sight to the top of the moss support. */
    private BlockPos approachPosition(BlockPos site) {
        BlockPos best=null;double bestDistance=Double.POSITIVE_INFINITY;
        boolean selfBlocked=mc.player.getBoundingBox().intersects(new Box(site));
        boolean entityBlocked=!blockers(site).isEmpty();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=-1;dy<=1;dy++) {
            if(dx==0 && dz==0)continue;
            BlockPos feet=site.add(dx,dy,dz);
            // GoalBlock only checks the block coordinate; pick another cell when the player overlaps the site.
            if(selfBlocked && feet.equals(mc.player.getBlockPos()))continue;
            if(entityBlocked && (dx*dx+dz*dz>1 || dy!=0))continue;
            if(!mc.world.isChunkLoaded(feet) || !RegionConstraint.allows(feet.getX(),feet.getY(),feet.getZ())
                || !mc.world.getWorldBorder().contains(feet) || !mc.world.getBlockState(feet).isAir()
                || !mc.world.getBlockState(feet.up()).isAir() || !mc.world.getBlockState(feet.down()).getFluidState().isEmpty()
                || !mc.world.getBlockState(feet.down()).isSideSolidFullSquare(mc.world,feet.down(),Direction.UP))continue;
            if(!mc.world.getOtherEntities(mc.player,new Box(feet).stretch(0,1,0),e->e instanceof net.minecraft.entity.LivingEntity && !e.isSpectator()).isEmpty())continue;
            Vec3d eye=Vec3d.ofBottomCenter(feet).add(0,mc.player.getStandingEyeHeight(),0);
            BlockHitResult hit=hit(site);double reach=Math.min(4.25,mc.player.getBlockInteractionRange()-.15);
            if(eye.squaredDistanceTo(hit.getPos())>reach*reach)continue;
            var ray=mc.world.raycast(new RaycastContext(eye,hit.getPos().add(0,-.001,0),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,mc.player));
            if(ray.getType()!=HitResult.Type.BLOCK || !ray.getBlockPos().equals(site.down()) || ray.getSide()!=Direction.UP)continue;
            double distance=mc.player.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(feet));
            if(distance<bestDistance) { best=feet;bestDistance=distance; }
        }
        return best;
    }
    private void approach() {
        if(!stillAllowed(target) || !surface(target) || !structuralClear(target) || !mc.world.getBlockState(target.down()).isOf(Blocks.MOSS_BLOCK)) {
            reject("Light site no longer eligible");return;
        }
        if(blockers(target).stream().anyMatch(e->e.getBoundingBox().squaredMagnitude(mc.player.getEyePos())
            <=mc.player.getEntityInteractionRange()*mc.player.getEntityInteractionRange()) && visible(hit(target))) {
            if(!clearBlockers())return;
        }
        PathingSafety.acquire(this); // Short approach walks only: no mining or scaffolding.
        if(!approachSent) { NavigationTransport.go(approachGoal);navigationOwned=true;approachSent=true;approachTicks=0; }
        status="Approaching light position";
        // Let movement start, then place as soon as the real interaction reach and raycast permit it.
        if(++approachTicks>=2 && visible(hit(target))) {
            if(!clearBlockers())return;
            phase=Phase.PLACE;place();
        }
    }
    private boolean surface(BlockPos pos) {
        return mc.world.isChunkLoaded(pos) && RegionConstraint.allows(pos.getX(), pos.getY(), pos.getZ())
            && mc.world.getBlockState(pos).isAir() && mc.world.getBlockState(pos.up()).isAir()
            && mc.world.getBlockState(pos.down()).isSideSolidFullSquare(mc.world, pos.down(), Direction.UP);
    }
    private boolean clear(BlockPos pos) {
        // Rechecked both when selecting a site and immediately before the rotated interaction.
        return structuralClear(pos) && !mc.player.getBoundingBox().intersects(new Box(pos)) && blockers(pos).isEmpty();
    }
    private boolean structuralClear(BlockPos pos) {
        return supports.get().contains(mc.world.getBlockState(pos.down()).getBlock())
            && mc.world.getWorldBorder().contains(pos) && mc.world.getBlockEntity(pos.down())==null;
    }
    private List<net.minecraft.entity.Entity> blockers(BlockPos pos) {
        return mc.world.getOtherEntities(mc.player,new Box(pos),e->!e.isSpectator() && e.isAlive()
            && (e instanceof net.minecraft.entity.LivingEntity || e instanceof net.minecraft.entity.vehicle.BoatEntity
                || e instanceof net.minecraft.entity.vehicle.AbstractMinecartEntity));
    }
    private BlockHitResult hit(BlockPos pos) { return new BlockHitResult(Vec3d.ofBottomCenter(pos), Direction.UP, pos.down(), false); }
    private boolean visible(BlockHitResult hit) {
        Vec3d eye = mc.player.getEyePos(); double reach = Math.min(4.25, mc.player.getBlockInteractionRange()-.15);
        if (eye.squaredDistanceTo(hit.getPos()) > reach*reach) return false;
        var ray = mc.world.raycast(new RaycastContext(eye, hit.getPos().add(0, -.001, 0), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return ray.getType() == HitResult.Type.BLOCK && ray.getBlockPos().equals(hit.getBlockPos()) && ray.getSide() == Direction.UP;
    }
    private boolean stillAllowed(BlockPos pos) {
        LightGrid layout=grid();
        if (!layout.inside(pos.getX(),pos.getZ())) return false;
        var gaps=gapFiller();
        if (gaps!=null && gaps.isActive()) return true;
        return fixedGrid.get() && LightInventory.strength(expected)>=15
            && layout.sites(pos.getX(),pos.getX(),pos.getZ(),pos.getZ()).contains(new LightGrid.Site(pos.getX(),pos.getZ()));
    }
    private void place() {
        if (!MossInventory.playerInventoryReady()) return;
        BlockHitResult hit = hit(target);
        if (!stillAllowed(target) || !surface(target) || !structuralClear(target)) { reject("Light site is no longer eligible"); return; }
        if(!clearBlockers())return;
        if(!visible(hit) || !ItemStack.areEqual(expected,mc.player.getMainHandStack())) { retryPlacement("Placement interrupted");return; }
        if(++placementAttempts>3) { reject("Placement retries exhausted; site will be retried later");return; }
        confirmed = false; confirmTicks=0; phase = Phase.CONFIRM;
        Rotations.rotate(Rotations.getYaw(hit.getPos()), Rotations.getPitch(hit.getPos()), 100, () -> {
            if (!isActive() || phase != Phase.CONFIRM || world != mc.world || ActivityPause.isPaused() || !MossInventory.playerInventoryReady()
                || !stillAllowed(target) || !surface(target) || !clear(target) || !visible(hit) || !ItemStack.areEqual(expected, mc.player.getMainHandStack())) return;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit); mc.player.swingHand(Hand.MAIN_HAND);
        });
    }
    /** Retry planned holes even when neighboring lights already illuminate part of their coverage. */
    private Candidate findRetryCandidate() {
        if(!retryMissed.get() || !fixedGrid.get())return null;
        int source=LightInventory.hotbarLight();
        if(source<0)for(int i=0;i<36;i++)if(LightInventory.strength(mc.player.getInventory().getStack(i))>=15){source=i;break;}
        if(source<0 || LightInventory.strength(mc.player.getInventory().getStack(source))<15)return null;
        if(!(mc.player.getInventory().getStack(source).getItem() instanceof BlockItem item))return null;
        int r=retryRange.get();BlockPos origin=mc.player.getBlockPos();Candidate best=null;double nearest=Double.POSITIVE_INFINITY;
        for(var site:grid().sites(origin.getX()-r,origin.getX()+r,origin.getZ()-r,origin.getZ()+r,8192)) {
            for(int dy=-2;dy<=2;dy++) {
                BlockPos pos=new BlockPos(site.x(),origin.getY()+dy,site.z());
                double distance=mc.player.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(pos));
                if(distance>r*r || distance>=nearest || rejected.getOrDefault(pos,0)>clock || !surface(pos)
                    || !structuralClear(pos) || !mc.world.getBlockState(pos.down()).isOf(Blocks.MOSS_BLOCK)
                    || !item.getBlock().getDefaultState().canPlaceAt(mc.world,pos))continue;
                BlockPos approach=approachPosition(pos);
                if(approach==null && (!visible(hit(pos)) || mc.player.getBoundingBox().intersects(new Box(pos))))continue;
                best=new Candidate(pos,source,15,1,approach);nearest=distance;
            }
        }
        return best;
    }
    private void retryPlacement(String reason) {
        if(placementAttempts>=3){reject(reason+"; retrying this site later");return;}
        ItemStack current=mc.player.getInventory().getStack(slot);
        if(current.isEmpty() || !ItemStack.areItemsAndComponentsEqual(expected,current)){reject("Light supply changed");return;}
        expected=current.copy();InvUtils.swap(slot,false);
        approachGoal=approachPosition(target);approachSent=false;confirmTicks=0;
        if(navigationOwned)NavigationTransport.stop();
        phase=approachGoal==null ? Phase.PLACE : Phase.APPROACH;wait=4;status=reason+"; retrying";
    }
    /** Players are never attacked: keep the transaction paused until they leave the placement cell. */
    private boolean clearBlockers() {
        var entities=blockers(target);
        waitingForPlayer=entities.stream().anyMatch(e->e instanceof net.minecraft.entity.player.PlayerEntity);
        if(waitingForPlayer) {
            if(navigationOwned)NavigationTransport.stop();approachSent=false;
            status="Waiting for player to leave light position";return false;
        }
        if(mc.player.getBoundingBox().intersects(new Box(target))) {
            BlockPos aside=approachPosition(target);
            if(aside==null){reject("No standing space beside light position");return false;}
            if(!aside.equals(approachGoal)) {
                if(navigationOwned)NavigationTransport.stop();approachGoal=aside;approachSent=false;
            }
            phase=Phase.APPROACH;status="Moving out of light position";return false;
        }
        if(entities.isEmpty()) { InvUtils.swap(slot,false);return true; }
        var entity=entities.getFirst();status="Clearing entity from light position";
        double reach=mc.player.getEntityInteractionRange();
        if(entity.getBoundingBox().squaredMagnitude(mc.player.getEyePos())>reach*reach)return false;
        if(navigationOwned)NavigationTransport.stop();approachSent=false;
        if(mc.player.isUsingItem() || mc.player.getAttackCooldownProgress(0)<.95f || !mc.player.canSee(entity))return false;
        for(int i=0;i<9;i++)if(mc.player.getInventory().getStack(i).isIn(net.minecraft.registry.tag.ItemTags.SWORDS)) {InvUtils.swap(i,false);break;}
        Vec3d aim=entity.getBoundingBox().getCenter();
        Rotations.rotate(Rotations.getYaw(aim),Rotations.getPitch(aim),100,()->{
            if(!isActive() || mc.player==null || ActivityPause.isPaused() || target==null
                || entity instanceof net.minecraft.entity.player.PlayerEntity || !entity.isAlive()
                || !entity.getBoundingBox().intersects(new Box(target)) || !mc.player.canSee(entity)
                || entity.getBoundingBox().squaredMagnitude(mc.player.getEyePos())>reach*reach
                || blockers(target).stream().anyMatch(e->e instanceof net.minecraft.entity.player.PlayerEntity))return;
            mc.interactionManager.attackEntity(mc.player,entity);mc.player.swingHand(Hand.MAIN_HAND);
        });
        return false;
    }
    private void recordDeparture() {
        BlockPos pos=mc.player.getBlockPos().toImmutable();
        if(departureTrail.isEmpty() || !departureTrail.getLast().equals(pos)) {
            if(departureTrail.size()<4096)departureTrail.add(pos);
        }
    }
    private void finishPlacement() {
        if(!retryJourney){release();wait=interval.get();return;}
        retryJourneyCount++;
        Candidate next=retryJourneyCount<32 && departureTrail.size()<4095 ? findRetryCandidate() : null;
        if(next!=null){beginCandidate(next,true);return;}
        beginReturn();
    }
    private void loadTrail() {
        returnRoute.clear();
        for(int i=departureTrail.size()-1;i>=0;i--)returnRoute.addLast(departureTrail.get(i));
        while(!returnRoute.isEmpty() && returnRoute.peekFirst().equals(mc.player.getBlockPos()))returnRoute.removeFirst();
        returnFallback=true;
    }
    private void beginReturn() {
        recordDeparture();returning=true;waitingForPlayer=false;
        if(navigationOwned)NavigationTransport.stop();approachSent=false;returnGoal=null;
        loadTrail();returnOrigin=mc.player.getBlockPos();
        double trailCost=0;BlockPos last=returnOrigin;
        for(BlockPos step:returnRoute){trailCost+=Math.sqrt(last.getSquaredDistance(step));last=step;}
        var walker=Modules.get().get(LavacastPathfinder.class);
        if(walker!=null && walker.isActive() && trailCost>0) {
            returnSearch=new BoundedRouteSearch<>(returnOrigin,this::returnNeighbors,
                (a,b)->Math.sqrt(a.getSquaredDistance(b)),walker::lightingReturnWork,trailCost,32768);
            phase=Phase.RETURN_SEARCH;status="Finding shortest return to moss work";
        } else {phase=Phase.RETURN_WALK;}
        returnAge=returnStall=0;
    }
    /** Conservative dry walking edges: return routing never mines or builds through the roof. */
    private List<BlockPos> returnNeighbors(BlockPos from) {
        var walker=Modules.get().get(LavacastPathfinder.class);
        if(walker==null || !walker.isActive())return List.of();
        int r=walker.lightingReturnRadius(),height=walker.lightingReturnHeight();
        List<BlockPos> next=new ArrayList<>();
        for(Direction d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST))for(int dy=-1;dy<=1;dy++) {
            BlockPos p=from.offset(d).up(dy);
            if(Math.abs(p.getX()-returnOrigin.getX())>r || Math.abs(p.getZ()-returnOrigin.getZ())>r
                || Math.abs(p.getY()-returnOrigin.getY())>height || !standable(p))continue;
            if(dy>0 && !mc.world.getBlockState(from.up(2)).isAir())continue;
            if(dy<0 && !mc.world.getBlockState(p.up(2)).isAir())continue;
            next.add(p);
        }
        return next;
    }
    private boolean standable(BlockPos p) {
        return mc.world.isChunkLoaded(p) && RegionConstraint.allows(p.getX(),p.getY(),p.getZ())
            && mc.world.getWorldBorder().contains(p) && mc.world.getBlockState(p).isAir()
            && mc.world.getBlockState(p.up()).isAir() && mc.world.getBlockState(p.down()).getFluidState().isEmpty()
            && mc.world.getBlockState(p.down()).isSideSolidFullSquare(mc.world,p.down(),Direction.UP);
    }
    private void findReturnRoute() {
        if(!returnSearch.step(256))return;
        if(!returnSearch.route().isEmpty()) {
            returnRoute.clear();returnRoute.addAll(returnSearch.route());returnFallback=false;
        }
        returnSearch=null;phase=Phase.RETURN_WALK;returnGoal=null;approachSent=false;
    }
    private void finishReturn() {
        // Release both the placement pause and Baritone ownership before seeding fresh walker work.
        release();wait=100;status="Returned to moss work";
        var walker=Modules.get().get(LavacastPathfinder.class);
        if(walker!=null)walker.resumeAfterLighting();
    }
    private void walkReturn() {
        var engine=baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
        BlockPos feet=engine.getPlayerContext().playerFeet();
        var walker=Modules.get().get(LavacastPathfinder.class);
        // Arriving at usable work ends the detour, even if a waypoint was overshot or the old trail remains.
        if(mc.player.isOnGround() && walker!=null && walker.lightingReturnWork(feet)) {finishReturn();return;}
        if(mc.player.isOnGround() && ReturnRouteProgress.advance(returnRoute,feet)) {
            returnGoal=null;approachSent=false;
        }
        if(returnRoute.isEmpty()){finishReturn();return;}
        BlockPos goal=returnRoute.peekFirst();
        if(!goal.equals(returnGoal)){returnGoal=goal;returnAge=returnStall=0;returnBest=Double.POSITIVE_INFINITY;approachSent=false;}
        double distance=mc.player.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(goal));
        if(distance<returnBest-.04){returnBest=distance;returnStall=0;}else returnStall++;
        if(++returnAge>200 || returnStall>80) {
            if(!returnFallback){
                // Cancel the failed goal before replacing it; never leave Baritone working on both routes.
                NavigationTransport.stop();loadTrail();returnGoal=null;approachSent=false;return;
            }
            warning("Lighting return route is blocked; releasing control to the pathfinder.");finishReturn();return;
        }
        PathingSafety.acquire(this);
        boolean idle=!engine.getCustomGoalProcess().isActive()
            && !engine.getPathingBehavior().isPathing() && engine.getPathingBehavior().getInProgress().isEmpty();
        // A cancelled goal may have no executor left. Reissue at a bounded interval instead of waiting idle.
        if(!approachSent || (idle && returnAge%20==0)) {
            NavigationTransport.go(goal);navigationOwned=true;approachSent=true;
        }
        status=returnFallback ? "Retracing lighting trip" : "Returning to nearby moss work";
    }
    private void drawDebug(meteordevelopment.meteorclient.events.render.Render3DEvent event) {
        if (fixedGrid.get()) drawGrid(event);
        // Inactive preview renders the plan only, never stale placement targets or sampled darkness.
        if (!isActive()) return;
        var pos=mc.player.getPos();
        debug.circle(event,pos.x,pos.y+.05,pos.z,radius.get(),debug.line(255,210,50));
        if (phase!=Phase.IDLE && target!=null) debug.box(event,target,60,255,100);
    }
    /** Cached terrain lookup; markers are generated around the camera, including Freecam, in loaded chunks. */
    private void drawGrid(meteordevelopment.meteorclient.events.render.Render3DEvent event) {
        long now=System.nanoTime();
        if (markerWorld!=mc.world || now>=nextMarkers) {
            markerWorld=mc.world;
            nextMarkers=now+500_000_000L;
            Vec3d camera=mc.gameRenderer.getCamera().getPos();
            int distance=mc.options.getViewDistance().getValue()*16;
            int x=MathHelper.floor(camera.x),z=MathHelper.floor(camera.z);
            List<GridMarker> markers=new ArrayList<>();
            for (LightGrid.Site site:grid().sites(x-distance,x+distance,z-distance,z+distance,8192)) {
                if (markers.size()>=8192) break; // Bound exceptionally dense high-light-threshold configurations.
                BlockPos probe=new BlockPos(site.x(),mc.player.getBlockY(),site.z());
                if (!mc.world.isChunkLoaded(probe)) continue;
                int top=mc.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE,site.x(),site.z());
                BlockPos air=new BlockPos(site.x(),top,site.z());
                boolean complete=mc.world.getBlockState(air.down()).getLuminance()>=15;
                boolean ready=supports.get().contains(mc.world.getBlockState(air.down()).getBlock()) && surface(air);
                markers.add(new GridMarker(complete ? air.down() : air,complete,ready));
            }
            gridMarkers=List.copyOf(markers);
        }
        for (GridMarker marker:gridMarkers) {
            if (marker.complete) debug.box(event,marker.pos,60,200,80);
            else if (marker.ready) debug.box(event,marker.pos,60,200,255);
            else debug.box(event,marker.pos,255,170,40);
        }
    }
    private void reject(String reason) {
        if (target != null) rejected.put(target, clock+200);
        if(navigationOwned)NavigationTransport.stop();navigationOwned=false;
        restoreStack();status=reason;
        if(retryJourney && !returning)beginReturn();else { release();wait=20; }
    }
    /** Return a temporarily borrowed hotbar slot without ever putting an item on the cursor. */
    private void restoreStack() {
        if (storageSlot < 9 || mc.player == null || mc.interactionManager == null) return;
        if (mc.player.currentScreenHandler == mc.player.playerScreenHandler && mc.player.currentScreenHandler.getCursorStack().isEmpty()
            && !mc.player.isUsingItem() && ItemStack.areEqual(displaced, mc.player.getInventory().getStack(storageSlot))) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            // A live light stack remains in this slot until exhausted; never swap it back out.
            if (stack.isEmpty())
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, storageSlot, slot, SlotActionType.SWAP, mc.player);
        }
        storageSlot = -1;
    }
    private void release() {
        if(navigationOwned)NavigationTransport.stop();
        navigationOwned=false;approachSent=false;approachGoal=null;
        waitingForPlayer=false;retryJourney=false;returning=false;departureTrail.clear();returnRoute.clear();returnSearch=null;returnGoal=null;
        restoreStack();
        if (previous >= 0 && mc.player != null && mc.interactionManager != null) InvUtils.swap(previous, false);
        previous = -1; phase = Phase.IDLE; PathingSafety.release(this); InventoryGuard.release(this);
    }
    @Override public String getInfoString() { return status; }
}
