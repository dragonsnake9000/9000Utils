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
    private List<BlockPos> debugDark = List.of();
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<List<Block>> supports = general.add(new BlockListSetting.Builder().name("placement-support-blocks")
        .description("Only place lights directly on these blocks. Defaults to moss; an empty list disables light placement.")
        .defaultValue(Blocks.MOSS_BLOCK).build());
    private final Setting<Integer> interval = general.add(new IntSetting.Builder().name("scan-interval-ticks").description("Ticks between local roof scans.").defaultValue(20).range(5, 1200).sliderRange(5, 100).build());
    private final Setting<Integer> radius = general.add(new IntSetting.Builder().name("coverage-scan-radius").description("Loaded roof radius scored around the player; travel remains controlled by Lavacast Pathfinder.").defaultValue(16).range(4, 24).sliderRange(4, 24).build());
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
    private enum Phase { IDLE, SELECT, PLACE, CONFIRM, RESTORE }
    private Phase phase = Phase.IDLE;
    private int clock, wait, age, slot, previous = -1;
    private boolean ownedRefill;
    private volatile boolean confirmed;
    private BlockPos target;
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
        AutomationContext.acquire(this); PathingSafety.protectLights(true); world = mc.world; phase = Phase.IDLE; wait = 0; rejected.clear(); debugDark=List.of(); gridMarkers=List.of(); nextMarkers=0;
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
        if (ActivityPause.isPaused() || AutomationContext.blockedScreen()) return;
        clock++;
        if (phase != Phase.IDLE) {
            if (++age > 80) { reject("Placement timed out"); return; }
            if (wait-- > 0) return;
            switch (phase) {
                case SELECT -> {
                    if (!MossInventory.playerInventoryReady()) return;
                    if (!ItemStack.areEqual(expected, mc.player.getInventory().getStack(slot))) { reject("Hotbar transfer changed"); return; }
                    InvUtils.swap(slot, false); phase = Phase.PLACE; wait = 2;
                }
                case PLACE -> place();
                case CONFIRM -> {
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
                        restoreStack(); phase = Phase.RESTORE; wait = 4; status = "Light placed";
                    }
                }
                case RESTORE -> { release(); wait = 20; }
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
        // One representative per light strength/block: the strongest valid placement wins coverage ties.
        List<BlockPos> dark = new ArrayList<>();
        BlockPos origin = mc.player.getBlockPos();
        int r = radius.get();
        LightGrid layout=grid();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) for (int y = -2; y <= 2; y++) {
            BlockPos pos = origin.add(x, y, z);
            if (layout.inside(pos.getX(),pos.getZ()) && surface(pos) && mc.world.getLightLevel(LightType.BLOCK, pos) < minimum.get()) dark.add(pos);
        }
        debugDark=List.copyOf(dark);
        if (dark.isEmpty()) { status = "Nearby surfaces lit"; return; }
        Candidate best = fixedGrid.get() ? choose(dark,true,1) : null;
        var gaps=gapFiller();
        if (best==null && gaps!=null && gaps.isActive()) best=choose(dark,false,gaps.minimumNewlyLit());
        if (best == null) {
            status = LightInventory.count()==0 ? "Waiting for compatible light supplies"
                : fixedGrid.get() ? "Waiting for reachable grid position" : "Grid off; enable Roof Light Gap Filler for cleanup";
            return;
        }
        if (!InventoryGuard.acquire(this)) return;
        target = best.pos; expected = mc.player.getInventory().getStack(best.source).copy();
        previous = mc.player.getInventory().selectedSlot;
        slot = best.source < 9 ? best.source : MossInventory.safeHotbarSlot(previous);
        if (slot < 0) { InventoryGuard.release(this); status="No non-sword hotbar slot available"; return; }
        if (best.source >= 9) {
            int existingLight=-1;
            for (int i=0;i<9;i++) if (LightInventory.strength(mc.player.getInventory().getStack(i))>0) { existingLight=i; break; }
            if (existingLight>=0) slot=existingLight;
            else for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isEmpty()) { slot = i; break; }
            storageSlot = best.source;
            displaced = mc.player.getInventory().getStack(slot).copy();
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, MossInventory.playerSlotId(best.source), slot, SlotActionType.SWAP, mc.player);
        }
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (walker != null && walker.isActive()) walker.pauseForRefill();
        NavigationTransport.stop(); PathingSafety.acquire(this);
        phase = Phase.SELECT; age = 0; wait = 4; status = "Placing light";
    }
    /** Grid sites have priority; opportunistic candidates exist only with the cleanup module active. */
    private Candidate choose(List<BlockPos> dark,boolean gridOnly,int threshold) {
        BlockPos origin=mc.player.getBlockPos();
        LightGrid layout=grid();
        Set<LightGrid.Site> planned=gridOnly ? layout.sites(origin.getX()-4,origin.getX()+4,origin.getZ()-4,origin.getZ()+4) : Set.of();
        List<Candidate> candidates = new ArrayList<>();
        Set<net.minecraft.block.Block> seen = new HashSet<>();
        for (int source = 0; source < 36; source++) {
            ItemStack stack = mc.player.getInventory().getStack(source);
            int strength = LightInventory.strength(stack);
            if ((gridOnly && strength<15) || strength < minimum.get() || !(stack.getItem() instanceof BlockItem item) || !seen.add(item.getBlock())) continue;
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) for (int y = -2; y <= 2; y++) {
                BlockPos pos = origin.add(x, y, z);
                if (!layout.inside(pos.getX(),pos.getZ()) || (gridOnly && !planned.contains(new LightGrid.Site(pos.getX(),pos.getZ())))) continue;
                if (rejected.containsKey(pos) || !surface(pos) || !clear(pos) || !item.getBlock().getDefaultState().canPlaceAt(mc.world, pos)
                    || !visible(hit(pos))) continue;
                int score = 0;
                for (BlockPos cell : dark) if (LightCoverage.lightAt(strength, cell.getX()-pos.getX(), cell.getY()-pos.getY(), cell.getZ()-pos.getZ()) >= minimum.get()) score++;
                if (score >= threshold) candidates.add(new Candidate(pos, source, strength, score));
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
    private record Candidate(BlockPos pos, int source, int strength, int score) { }

    /** Conservative flood fill through air. Real world light is checked again after each placement. */
    private int reachableCoverage(Candidate candidate, List<BlockPos> dark) {
        Map<BlockPos, Integer> light = LightCoverage.propagate(candidate.pos, candidate.strength, minimum.get(),
            pos -> List.of(pos.up(), pos.down(), pos.north(), pos.south(), pos.east(), pos.west()),
            pos -> mc.world.isChunkLoaded(pos) && mc.world.getBlockState(pos).isAir());
        int score = 0;
        for (BlockPos pos : dark) if (light.getOrDefault(pos, 0) >= minimum.get()) score++;
        return score;
    }
    private boolean surface(BlockPos pos) {
        return mc.world.isChunkLoaded(pos) && RegionConstraint.allows(pos.getX(), pos.getY(), pos.getZ())
            && mc.world.getBlockState(pos).isAir() && mc.world.getBlockState(pos.up()).isAir()
            && mc.world.getBlockState(pos.down()).isSideSolidFullSquare(mc.world, pos.down(), Direction.UP);
    }
    private boolean clear(BlockPos pos) {
        // Rechecked both when selecting a site and immediately before the rotated interaction.
        return supports.get().contains(mc.world.getBlockState(pos.down()).getBlock())
            && mc.world.getWorldBorder().contains(pos) && mc.world.getBlockEntity(pos.down()) == null
            && !mc.player.getBoundingBox().intersects(new Box(pos))
            && mc.world.getOtherEntities(mc.player, new Box(pos), e -> !e.isSpectator()).isEmpty();
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
        if (!stillAllowed(target) || !surface(target) || !clear(target) || !visible(hit) || !ItemStack.areEqual(expected, mc.player.getMainHandStack())) { reject("Placement changed"); return; }
        confirmed = false; phase = Phase.CONFIRM;
        Rotations.rotate(Rotations.getYaw(hit.getPos()), Rotations.getPitch(hit.getPos()), 100, () -> {
            if (!isActive() || phase != Phase.CONFIRM || world != mc.world || ActivityPause.isPaused() || !MossInventory.playerInventoryReady()
                || !stillAllowed(target) || !surface(target) || !clear(target) || !visible(hit) || !ItemStack.areEqual(expected, mc.player.getMainHandStack())) return;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit); mc.player.swingHand(Hand.MAIN_HAND);
        });
    }
    private void drawDebug(meteordevelopment.meteorclient.events.render.Render3DEvent event) {
        if (fixedGrid.get()) drawGrid(event);
        // Inactive preview renders the plan only, never stale placement targets or sampled darkness.
        if (!isActive()) return;
        var pos=mc.player.getPos();
        debug.circle(event,pos.x,pos.y+.05,pos.z,radius.get(),debug.line(255,210,50));
        int count=0;
        for (BlockPos cell:debugDark) {
            if (count++>=256) break;
            event.renderer.quadHorizontal(cell.getX(),cell.getY()+.02,cell.getZ(),cell.getX()+1,cell.getZ()+1,debug.fill(255,60,60));
        }
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
    private void reject(String reason) { if (target != null) rejected.put(target, clock+200); release(); wait = 20; status = reason; }
    /** Return a temporarily borrowed hotbar slot without ever putting an item on the cursor. */
    private void restoreStack() {
        if (storageSlot < 9 || mc.player == null || mc.interactionManager == null) return;
        if (mc.player.currentScreenHandler == mc.player.playerScreenHandler && mc.player.currentScreenHandler.getCursorStack().isEmpty()
            && !mc.player.isUsingItem() && ItemStack.areEqual(displaced, mc.player.getInventory().getStack(storageSlot))) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || ItemStack.areItemsAndComponentsEqual(stack, expected))
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, storageSlot, slot, SlotActionType.SWAP, mc.player);
        }
        storageSlot = -1;
    }
    private void release() {
        restoreStack();
        if (previous >= 0 && mc.player != null && mc.interactionManager != null) InvUtils.swap(previous, false);
        previous = -1; phase = Phase.IDLE; PathingSafety.release(this); InventoryGuard.release(this);
    }
    @Override public String getInfoString() { return status; }
}
