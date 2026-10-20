package dev.dragonsnake9000.utils;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import java.util.*;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Deliberately serial, paced controller. Never transfers items via packet bursts or air placement. */
public abstract class SupplyRefill extends Module {
    enum State { IDLE, HOTBAR_SETTLE, SETTLE, PREPARE, SELECT, PLACE, WAIT_PLACE, OPEN, WAIT_OPEN, TRANSFER, MERGE_CARRY, MERGE_RETURN, WAIT_TRANSFER, CLOSE, MINE, PICKUP, CAP_SELECT, CAP_AIM, CAP_ROUTE, CAP_PLACE, CAP_WAIT, FINISH, FAILED }
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<Integer> delay = general.add(new IntSetting.Builder().name("action-delay-ticks")
        .description("Ticks between inventory and interaction actions. Raise for laggy servers.").defaultValue(2).range(0, 1200).sliderRange(0, 40).build());
    private final Setting<Integer> timeout = general.add(new IntSetting.Builder().name("step-timeout-seconds")
        .description("Stop for manual recovery if a refill step cannot complete.").defaultValue(15).range(1, 3600).sliderRange(1, 120).build());
    private final Setting<Boolean> onlyWalking = general.add(new BoolSetting.Builder().name("only-while-walker-enabled")
        .description("Start automatic refills only when Lavacast Pathfinder is enabled.").defaultValue(true).visible(() -> !this.lighting).build());
    private final Setting<Boolean> replenish = general.add(new BoolSetting.Builder().name("replenish-hotbar")
        .description("Check every tick and replace low moss stacks without pausing movement.").defaultValue(true).visible(() -> !this.lighting).build());
    private final Setting<Integer> replenishThreshold = general.add(new IntSetting.Builder().name("hotbar-refill-threshold")
        .description("Replace the first hotbar moss stack at or below this amount when a larger reserve stack is available. Movement is not paused.")
        .defaultValue(16).range(1,63).sliderRange(1,63).visible(() -> !this.lighting && replenish.get()).build());
    private int replenishedSlot=-1;
    private boolean consolidating;
    private final Setting<Boolean> consolidate = general.add(new BoolSetting.Builder().name("consolidate-moss-stacks")
        .description("Combine matching partial moss stacks in main inventory whenever they can make a full stack. Walking continues.")
        .defaultValue(true).visible(() -> !this.lighting && replenish.get()).build());
    private final Setting<Boolean> returnPosition = general.add(new BoolSetting.Builder().name("return-to-refill-position")
        .description("Walk back to the standing block saved when placing the shulker if pushed away. Keeps the refill screen open and yields to eating/KillAura attacks.")
        .defaultValue(true).build());
    private RefillAnchor anchor;
    private boolean returning;
    private int returnTicks;
    private final NavigationBudget<BlockPos> returnCommands=new NavigationBudget<>();
    private final Setting<Boolean> capBoxes = general.add(new BoolSetting.Builder().name("lower-placement-and-cap")
        .description("Prefer a lower supply position, fall back anywhere reachable, then navigate to cap empty boxes. Off skips both lower preference and capping.").defaultValue(true).visible(() -> !this.lighting).build());
    private final Setting<Integer> confirmSeconds = general.add(new IntSetting.Builder().name("placement-confirmation-seconds")
        .description("Wait for server placement before trying another location; includes cap confirmation.").defaultValue(3).range(1, 30).sliderRange(1, 10).build());
    private final Setting<Boolean> safeSupply = general.add(new BoolSetting.Builder().name("avoid-shulker-edges")
        .description("Require surrounding floor blocks before placing a supply box. Stop rather than fall back to an unsafe edge.").defaultValue(true).build());
    private final Setting<Block> preferredSupport = general.add(new BlockSetting.Builder().name("preferred-shulker-support")
        .description("Prefer this block below the shulker among locations meeting the floor requirement.").defaultValue(Blocks.MOSS_BLOCK).visible(safeSupply::get).build());
    private final Setting<Integer> supportNeighbors = general.add(new IntSetting.Builder().name("shulker-support-neighbors")
        .description("Required solid floor neighbors at the support's Y level, including diagonals. Eight requires a complete 3x3 floor; zero permits edges.")
        .defaultValue(8).range(0, 8).sliderRange(0, 8).visible(safeSupply::get).build());
    private State state = State.IDLE;
    private ClientWorld world;
    private BlockPos placedPos;
    private Block placedBlock;
    private Item shulkerItem;
    private BlockHitResult placeHit;
    private int hotbar, previousSlot = -1, waitTicks, stateTicks, containerId = -1;
    private int mossBeforeClick, boxBaseline;
    private int mergeSource, mergeDestination;
    private ItemStack mergeStack, mergeTarget;
    private boolean recoverBox;
    private volatile int receivedInventoryId = -1;
    private volatile boolean serverPlaced;
    private ItemStack selectedBox;
    private boolean activityPaused;
    private boolean interactionSent;
    private final NavigationBudget<BlockPos> pickupCommands = new NavigationBudget<>();
    private volatile boolean capConfirmed;
    private int capSlot;
    private boolean capSkipped;
    private boolean anywhere, lowerAttempt;
    private int placementAttempts, capAttempts;
    private final Set<BlockPos> rejectedPlaces = new HashSet<>(), rejectedVantage = new HashSet<>();
    private BlockPos routeGoal;
    private int routeTicks, routeStall;
    private double routeBest;
    private int pickupEntityId = -1;
    private final Set<Integer> previousDrops = new HashSet<>();
    private String status = "Waiting";

    private boolean lighting;
    protected SupplyRefill(boolean lighting) {
        super(Utils9000Addon.CATEGORY, lighting ? "light-shulker-refill" : "moss-shulker-refill",
            lighting ? "Refills light blocks from inventory shulkers, reserving room to recover nonempty boxes." : "Refills moss from inventory shulkers, recovering boxes which still contain moss.");
        this.lighting = lighting;
        runInMainMenu = true;
    }

    public boolean shouldPauseWalking() {
        return isActive() && mc.player != null && ((state != State.IDLE && state != State.HOTBAR_SETTLE) || supplyCount() == 0);
    }
    public boolean transactionActive() { return isActive() && state != State.IDLE && state != State.HOTBAR_SETTLE; }
    public boolean placingSupplyBox() { return isActive() && state == State.WAIT_PLACE; }
    public boolean openingSupplyBox(BlockPos position) {
        return isActive() && (state == State.OPEN || state == State.WAIT_OPEN) && position.equals(placedPos);
    }

    @Override public void onActivate() {
        AutomationContext.acquire(this);
        state = State.IDLE;
        activityPaused = false;
        waitTicks = 0;
        placedPos = null; anchor=null; returning=false; returnCommands.reset();
        world = mc.world;
        status = "Waiting";
    }

    @Override public void onDeactivate() {
        AutomationContext.release(this);
        if (state != State.IDLE) {
            stopBaritone();
            if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
            if (mc.player != null && mc.player.currentScreenHandler.syncId == containerId) mc.player.closeHandledScreen();
            if (placedPos != null && state != State.FINISH) warning("Refill interrupted. Check shulker at %s.", placedPos.toShortString());
        }
        restore();
        state = State.IDLE;
    }

    @EventHandler private void leave(GameLeftEvent event) { if (isActive()) toggle(); }
    @EventHandler private void receive(PacketEvent.Receive event) {
        if (event.packet instanceof InventoryS2CPacket packet) receivedInventoryId = packet.getSyncId();
        if (event.packet instanceof BlockUpdateS2CPacket packet) observeBlock(packet.getPos(), packet.getState());
        if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) packet.visitUpdates(this::observeBlock);
    }

    private void observeBlock(BlockPos pos, BlockState block) {
        if (placedPos != null && placedPos.equals(pos) && block.isOf(placedBlock)) serverPlaced = true;
        if (placedPos != null && placedPos.up().equals(pos) && block.isOf(net.minecraft.block.Blocks.MOSS_BLOCK)) capConfirmed = true;
    }

    @EventHandler(priority = -100) private void tick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!mc.player.isAlive()) { if (isActive()) toggle(); return; }
        if (state != State.IDLE) InventoryGuard.maintain(this);
        if (state != State.IDLE && world != mc.world) { fail("Dimension changed during refill; recover the box manually."); return; }
        if (state == State.FAILED) return;
        boolean otherScreen = AutomationContext.blockedScreen() && !(mc.currentScreen instanceof ShulkerBoxScreen
            && (state == State.WAIT_OPEN || state == State.TRANSFER || state == State.WAIT_TRANSFER
                || state == State.MERGE_CARRY || state == State.MERGE_RETURN));
        if (ActivityPause.isPaused() || otherScreen || (anchor!=null && refillCombatBusy())) {
            if (!activityPaused && state != State.IDLE && state != State.HOTBAR_SETTLE) {
                stopBaritone();
                pickupCommands.clear(); returnCommands.clear();
                mc.interactionManager.cancelBlockBreaking();
            }
            activityPaused = true;
            status = otherScreen ? "Paused while settings/inventory is open" : "Paused for combat/eating";
            return;
        }
        if (activityPaused) {
            activityPaused = false;
            if (!interactionSent && state == State.WAIT_PLACE) state = State.PLACE;
            if (!interactionSent && state == State.WAIT_OPEN) state = State.OPEN;
            if (!interactionSent && state == State.CAP_WAIT) state = State.CAP_PLACE;
            if (state == State.MINE) selectTool();
            if (state == State.PLACE) InvUtils.swap(hotbar, false);
            if (state == State.CAP_PLACE) InvUtils.swap(capSlot, false);
            if (state == State.CAP_ROUTE || state == State.PICKUP) routeGoal = null;
            waitTicks = Math.max(waitTicks, delay.get());
        }
        if (recoverPosition()) return;
        if (state == State.IDLE) {
            // Hotbar supply has its own fast cadence, independent of shulker scans/action delays.
            var activeWalker=Modules.get().get(LavacastPathfinder.class);
            if (!lighting && replenish.get() && (!onlyWalking.get() || (activeWalker!=null && activeWalker.isActive()))
                && !otherRefillBusy() && !RoofLighting.placing() && replenishHotbar()) return;
            if (waitTicks-- > 0) return;
            waitTicks = 20;
            LavacastPathfinder walker = Modules.get().get(LavacastPathfinder.class);
            if (lighting) {
                RoofLighting roof = Modules.get().get(RoofLighting.class);
                if (roof == null || !roof.isActive() || RoofLighting.placing()) return;
            } else if (onlyWalking.get() && (walker == null || !walker.isActive())) return;
            if (otherRefillBusy() || (!lighting && RoofLighting.placing())) return;
            // Let the pathfinder establish its bounded region-entry corridor before placing a supply outside.
            if (!RegionConstraint.allows(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ())) return;
            if (supplyCount() > 0) {
                status = lighting ? "Lights available" : "Moss available";
                return;
            }
            if (findSupplyBox() < 0) { exhausted(); return; }
            status = lighting ? "Refilling lights" : "Refilling moss";
            if (!MossInventory.playerInventoryReady() || !mc.player.isOnGround()) return;
            if (!InventoryGuard.acquire(this)) return;
            world = mc.world;
            previousSlot = mc.player.getInventory().selectedSlot;
            PathingSafety.acquire(this);
            if (walker != null && walker.isActive()) walker.pauseForRefill();
            stopBaritone();
            placedPos = null;
            receivedInventoryId = -1;
            containerId = -1;
            pickupCommands.reset();
            resetBoxAttempt();
            enter(State.SETTLE);
            return;
        }
        if (++stateTicks > timeout.get() * 20) {
            if (state == State.CAP_SELECT || state == State.CAP_AIM || state == State.CAP_ROUTE || state == State.CAP_PLACE || state == State.CAP_WAIT)
                skipCap("Capping timed out.");
            else if (state == State.WAIT_PLACE) retryPlacement();
            else fail("Timed out during " + state + ".");
            return;
        }
        if (waitTicks-- > 0) return;

        switch (state) {
            case SETTLE -> {
                if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()
                    && mc.player.isOnGround() && mc.player.getVelocity().horizontalLengthSquared() < .01) enter(State.PREPARE);
            }
            case HOTBAR_SETTLE -> {
                if (!MossInventory.playerInventoryReady()) return;
                if (consolidating) status="Partial moss stacks combined";
                else if (replenishedSlot<0 || !mc.player.getInventory().getStack(replenishedSlot).isOf(Items.MOSS_BLOCK)) {
                    // Release and retry through the next tick's live inventory plan; never replay a stale click.
                    status="Hotbar changed; rechecking moss";
                } else status="Hotbar moss ready";
                replenishedSlot=-1; consolidating=false;
                InventoryGuard.release(this);
                state = State.IDLE;
                waitTicks = 0;
            }
            case PREPARE -> prepare();
            case SELECT -> {
                if (!MossInventory.playerInventoryReady()) return;
                if (!ItemStack.areEqual(mc.player.getInventory().getStack(hotbar), selectedBox)) { fail("Shulker hotbar transfer was corrected."); return; }
                InvUtils.swap(hotbar, false);
                enter(State.PLACE);
            }
            case PLACE -> place();
            case WAIT_PLACE -> {
                if (serverPlaced && world.getBlockState(placedPos).isOf(placedBlock) && !mc.player.getInventory().getStack(hotbar).isOf(shulkerItem)) {
                    boxBaseline = countBoxes();
                    enter(State.OPEN);
                }
                else if (stateTicks >= confirmSeconds.get() * 20) retryPlacement();
            }
            case OPEN -> open();
            case WAIT_OPEN -> {
                if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler && receivedInventoryId == handler.syncId) {
                    containerId = handler.syncId;
                    enter(State.TRANSFER);
                }
            }
            case TRANSFER -> transfer();
            case MERGE_CARRY -> mergeIntoStack();
            case MERGE_RETURN -> returnRemainder();
            case WAIT_TRANSFER -> {
                if (!ownContainer()) { fail("Shulker screen changed during transfer."); return; }
                if (supplyCount() > mossBeforeClick) enter(State.TRANSFER);
            }
            case CLOSE -> {
                if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) { fail("Another inventory opened during recovery."); return; }
                if (recoverBox) {
                    previousDrops.clear();
                    for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(placedPos).expand(16, 64, 16), entity -> entity.getStack().isOf(shulkerItem)))
                        previousDrops.add(item.getId());
                    pickupEntityId = -1; selectTool(); enter(State.MINE);
                }
                else if (capEnabled()) { enter(State.CAP_SELECT); waitTicks = Math.max(12, waitTicks); }
                else { capSkipped = true; enter(State.FINISH); }
            }
            case MINE -> mine();
            case PICKUP -> pickup();
            case CAP_SELECT -> prepareCap();
            case CAP_AIM -> {
                if (!MossInventory.playerInventoryReady()) return;
                if (!mc.player.getInventory().getStack(capSlot).isOf(Items.MOSS_BLOCK)) { skipCap("Moss cap slot changed."); return; }
                InvUtils.swap(capSlot, false);
                enter(State.CAP_PLACE);
                waitTicks = Math.max(2, waitTicks);
            }
            case CAP_ROUTE -> capRoute();
            case CAP_PLACE -> placeCap();
            case CAP_WAIT -> {
                if (capConfirmed && world.getBlockState(placedPos.up()).isOf(net.minecraft.block.Blocks.MOSS_BLOCK)) {
                    enter(State.FINISH);
                }
                else if (stateTicks >= confirmSeconds.get() * 20) skipCap("The server did not confirm the cap.");
            }
            case FINISH -> finish();
            default -> { }
        }
    }

    private boolean capEnabled() { return !lighting && capBoxes.get(); }
    private boolean isSupply(ItemStack stack) { return lighting ? LightInventory.isLight(stack) : stack.isOf(Items.MOSS_BLOCK); }
    private int supplyCount() { return lighting ? LightInventory.count() : MossInventory.countMoss(); }
    private int findSupplyBox() { return lighting ? LightInventory.findBox() : MossInventory.findMossShulker(); }
    private boolean otherRefillBusy() {
        SupplyRefill other = lighting ? Modules.get().get(MossRefill.class) : Modules.get().get(LightRefill.class);
        return other != null && other.transactionActive();
    }
    private void exhausted() {
        if (!lighting) { AutomationShutdown.noMoss(); return; }
        warning("No light blocks detected in inventory or supply shulkers. Roof Lighting stopped; mossing can continue.");
        RoofLighting roof = Modules.get().get(RoofLighting.class);
        if (roof != null && roof.isActive()) roof.toggle();
        if (isActive()) toggle();
    }

    private void prepare() {
        if (!MossInventory.playerInventoryReady()) return;
        int source = findSupplyBox();
        if (source < 0) { fail("No matching supply shulker remains in inventory."); return; }
        lowerAttempt = capEnabled() && !anywhere;
        placeHit = findPlacement(lowerAttempt);
        if (placeHit == null && lowerAttempt) {
            anywhere = true; lowerAttempt = false; placeHit = findPlacement(false);
        }
        if (placeHit == null) { fail(safeSupply.get() ? "No reachable shulker site meets the floor-neighbor requirement. Move onto a wider platform or lower Shulker Support Neighbors." : "No visible reachable space for a supply shulker."); return; }
        capSkipped = false;
        ItemStack box = mc.player.getInventory().getStack(source);
        selectedBox = box.copy();
        shulkerItem = box.getItem();
        placedBlock = ((BlockItem) shulkerItem).getBlock();
        hotbar = source < 9 ? source : MossInventory.safeHotbarSlot(mc.player.getInventory().selectedSlot);
        if (hotbar < 0) { fail("No non-sword hotbar slot available for refill."); return; }
        if (source >= 9) {
            for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isEmpty()) { hotbar = i; break; }
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, MossInventory.playerSlotId(source), hotbar, SlotActionType.SWAP, mc.player);
        }
        enter(State.SELECT);
    }

    private void place() {
        if (!MossInventory.playerInventoryReady()) return;
        BlockPos destination = placeHit.getBlockPos().up();
        if (!placementClear(destination) || !visible(placeHit) || !mc.player.getMainHandStack().isOf(shulkerItem)) {
            retryPlacement(); return;
        }
        placedPos = destination.toImmutable();
        serverPlaced = false;
        enter(State.WAIT_PLACE);
        rotateInteract(placeHit, State.WAIT_PLACE);
    }

    private void open() {
        if (!MossInventory.playerInventoryReady()) return;
        if (!world.getBlockState(placedPos).isOf(placedBlock)) { fail("Placed shulker disappeared."); return; }
        BlockHitResult hit = visibleFace(placedPos);
        if (hit == null) { fail("Cannot see the placed shulker within reach."); return; }
        // The consumed shulker slot is empty. Do not open with moss/another block in hand.
        InvUtils.swap(hotbar, false);
        enter(State.WAIT_OPEN);
        rotateInteract(hit, State.WAIT_OPEN);
    }

    private void transfer() {
        if (!ownContainer() || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            fail("Inventory changed or an item is on the cursor; no transfer attempted."); return;
        }
        if (lighting && stowExtraLightStacks()) return;
        int source = -1, remaining = 0;
        for (int i = 0; i < 27; i++) {
            ItemStack s = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (isSupply(s)) { remaining += s.getCount(); if (source < 0) source = i; }
        }
        int merge = lighting ? 0 : MossInventory.mergeSpace();
        if (source >= 0 && !ItemStack.areItemsAndComponentsEqual(mc.player.currentScreenHandler.getSlot(source).getStack(), Items.MOSS_BLOCK.getDefaultStack())) merge = 0;
        // Complete a partial matching stack instead of overshooting the two-stack light target.
        boolean overLightTarget=lighting && source>=0 && supplyCount()<LightRefillTarget.BLOCKS
            && LightRefillTarget.wouldOverfill(supplyCount(),mc.player.currentScreenHandler.getSlot(source).getStack().getCount());
        if (overLightTarget && beginMerge(source)) return;
        if (overLightTarget || (lighting && (supplyCount() >= LightRefillTarget.BLOCKS || MossInventory.emptySlots() < 2)) || source < 0 || !RefillPlanner.canTakeStack(mc.player.currentScreenHandler.getSlot(source).getStack().getCount(), remaining, MossInventory.emptySlots(), merge)) {
            // A quick-move could consume the reserved box slot. Fill an existing partial stack explicitly instead.
            if (source >= 0 && merge > 0 && beginMerge(source)) return;
            recoverBox = remaining > 0;
            if (lighting) for (int i = 0; i < 27; i++) if (!mc.player.currentScreenHandler.getSlot(i).getStack().isEmpty()) recoverBox = true;
            if (recoverBox && MossInventory.emptySlots() < 1) { fail("Reserved shulker space was filled by another inventory action."); return; }
            mc.player.closeHandledScreen();
            enter(State.CLOSE);
            return;
        }
        mossBeforeClick = supplyCount();
        mc.interactionManager.clickSlot(containerId, source, 0, SlotActionType.QUICK_MOVE, mc.player);
        enter(State.WAIT_TRANSFER);
    }

    /** Keep one usable light stack in the hotbar; reserve stacks belong in main inventory. */
    private boolean stowExtraLightStacks() {
        int keep=-1,extra=-1;
        for (int i=0;i<9;i++) if (LightInventory.strength(mc.player.getInventory().getStack(i))>0) {
            if (keep<0) keep=i; else { extra=i; break; }
        }
        if (extra<0) return false;
        int main=-1;
        for (int i=9;i<36;i++) if (mc.player.getInventory().getStack(i).isEmpty()) { main=i; break; }
        if (main<0) {
            int emptyHotbar=-1;
            for (int i=0;i<9;i++) if (mc.player.getInventory().getStack(i).isEmpty()) { emptyHotbar=i; break; }
            if (emptyHotbar>=0) for (int i=9;i<36;i++) {
                ItemStack stack=mc.player.getInventory().getStack(i);
                if (!MossInventory.isShulker(stack) && LightInventory.strength(stack)==0) {
                    mc.interactionManager.clickSlot(containerId,27+i-9,emptyHotbar,SlotActionType.SWAP,mc.player);
                    main=i; break;
                }
            }
        }
        if (main<0) { fail("Free a main-inventory slot for the reserve light stack."); return true; }
        mc.interactionManager.clickSlot(containerId,27+main-9,extra,SlotActionType.SWAP,mc.player);
        enter(State.TRANSFER); // Wait for the inventory action before another shulker transfer.
        return true;
    }

    private void selectTool() {
        BlockState block = world.getBlockState(placedPos);
        int best = mc.player.getInventory().selectedSlot;
        float speed = mc.player.getInventory().getStack(best).getMiningSpeedMultiplier(block);
        for (int i = 0; i < 9; i++) {
            float candidate = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(block);
            if (candidate > speed) { best = i; speed = candidate; }
        }
        InvUtils.swap(best, false);
    }

    private void mine() {
        if (!MossInventory.playerInventoryReady()) return;
        if (!world.getBlockState(placedPos).isOf(placedBlock)) {
            mc.interactionManager.cancelBlockBreaking();
            if (!world.getBlockState(placedPos).isAir()) { fail("Shulker position was replaced by another block."); return; }
            enter(State.PICKUP);
            routeGoal = null;
            return;
        }
        BlockHitResult hit = visibleFace(placedPos);
        if (hit == null) { fail("Lost reach or line of sight while mining shulker."); return; }
        Rotations.rotate(Rotations.getYaw(hit.getPos()), Rotations.getPitch(hit.getPos()), 100, () -> {
            if (!isActive() || ActivityPause.isPaused() || AutomationContext.blockedScreen() || state != State.MINE || mc.world != world) return;
            mc.interactionManager.updateBlockBreakingProgress(placedPos, hit.getSide());
            mc.player.swingHand(Hand.MAIN_HAND);
        });
    }

    private void pickup() {
        if (countBoxes() > boxBaseline) { enter(State.FINISH); return; }
        if (world.getBlockState(placedPos).isOf(placedBlock)) {
            stopBaritone(); PathingSafety.acquire(this); routeGoal = null; selectTool(); enter(State.MINE); return;
        }
        // Follow the real item after it falls or slides, not the old shulker block coordinate.
        // Servers may omit container contents on dropped-item entities. Do not require their moss component.
        ItemEntity dropped = world.getEntityById(pickupEntityId) instanceof ItemEntity tracked && tracked.isAlive() ? tracked : null;
        if (dropped == null) dropped = world.getEntitiesByClass(ItemEntity.class, new Box(placedPos).expand(16, 64, 16),
            entity -> entity.isAlive() && entity.getStack().isOf(shulkerItem) && !previousDrops.contains(entity.getId()))
            .stream().min(Comparator.comparingDouble(entity -> entity.getPos().squaredDistanceTo(Vec3d.ofCenter(placedPos)))).orElse(null);
        if (dropped != null) {
            pickupEntityId = dropped.getId();
            if (!dropped.isOnGround() && Math.abs(dropped.getVelocity().y) > .08) { status = "Waiting for supply item to land"; return; }
            if (!RegionConstraint.allows(dropped.getBlockX(), dropped.getBlockY(), dropped.getBlockZ())) {
                fail("Supply item fell outside the allowed region. Recover it manually."); return;
            }
            if (!routeTo(dropped.getBlockPos())) { stopBaritone(); routeGoal = null; pickupCommands.clear(); }
            status = "Recovering supply item at " + dropped.getBlockPos().toShortString();
        }
        else status = "Waiting for dropped supply item";
    }

    private void finish() {
        stopBaritone();
        int count = supplyCount();
        if (count == 0 && recoverBox) { fail("No supply was transferred. Free inventory space before retrying."); return; }
        if (count == 0 && findSupplyBox() < 0) { exhausted(); return; }
        info("Refilled to %s " + (lighting ? "light blocks" : "moss") + ". %s", count, recoverBox ? "Shulker recovered."
            : capSkipped ? "Empty supply shulker left in place." : "Empty supply shulker capped with moss.");
        if ((!lighting || supplyCount() < LightRefillTarget.BLOCKS) && !recoverBox && findSupplyBox() >= 0 && MossInventory.emptySlots() * 64 + MossInventory.mergeSpace() > 0) {
            // Keep placement paused across all supply boxes in this refill batch.
            PathingSafety.acquire(this);
            resetBoxAttempt(); enter(State.SETTLE); return;
        }
        restore();
        state = State.IDLE;
        placedPos = null;
        waitTicks = 20;
        status = lighting ? "Lights available" : "Moss available";
    }

    private void fail(String reason) {
        if (state == State.FAILED) return;
        stopBaritone();
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        if (mc.player != null && mc.player.currentScreenHandler.syncId == containerId
            && mc.player.currentScreenHandler.getCursorStack().isEmpty()) mc.player.closeHandledScreen();
        state = State.FAILED;
        status = "Stopped: manual recovery needed";
        error("%s %s Toggle this refill module off after checking, then on to retry.", reason,
            placedPos == null ? "" : "Check shulker at " + placedPos.toShortString() + ".");
        // Retain ownership and keep walking/placing paused until the user resolves this.
    }

    private void restore() {
        anchor=null; returning=false; returnCommands.reset();
        if (previousSlot >= 0 && mc.player != null && mc.interactionManager != null) InvUtils.swap(previousSlot, false);
        previousSlot = -1;
        PathingSafety.release(this);
        ShulkerHotbarGuard.beforeRefillRelease();
        InventoryGuard.release(this);
    }

    private boolean replenishHotbar() {
        if (!MossInventory.playerInventoryReady()) return false;
        int[] moss=new int[41]; boolean[] empty=new boolean[9];
        for (int i=0;i<=40;i++) {
            if (i>=36 && i!=40) continue;
            ItemStack stack=mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.MOSS_BLOCK)) moss[i]=stack.getCount();
            if (i<9) empty[i]=stack.isEmpty();
        }
        var plan=MossHotbarPlan.choose(moss,empty,MossInventory.safeHotbarSlot(mc.player.getInventory().selectedSlot),replenishThreshold.get());
        boolean hotbarHasMoss=false;
        for (int i=0;i<9;i++) if (moss[i]>0) { hotbarHasMoss=true; break; }
        if (hotbarHasMoss && consolidate.get() && (plan==null || mc.player.getInventory().getStack(plan.source()).getCount()
            < mc.player.getInventory().getStack(plan.source()).getMaxCount()) && consolidateMoss()) return true;
        if (plan==null || !InventoryGuard.acquire(this)) return false;
        consolidating=false;
        // SWAP leaves the partial moss stack in the source slot. No item is left on the cursor.
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,MossInventory.playerSlotId(plan.source()),
            plan.destination(),SlotActionType.SWAP,mc.player);
        replenishedSlot=plan.destination(); world=mc.world;
        enter(State.HOTBAR_SETTLE);
        waitTicks=2; // Short hotbar settling, independent of potentially long shulker action delays.
        status="Topping up hotbar moss";
        return true;
    }

    /** Finish each pair in one client callback so no cursor stack is carried between ordinary ticks. */
    private boolean consolidateMoss() {
        for (int sample=9;sample<36;sample++) {
            ItemStack template=mc.player.getInventory().getStack(sample);
            if (!template.isOf(Items.MOSS_BLOCK) || template.getCount()>=template.getMaxCount()) continue;
            int[] counts=new int[36];
            for (int i=9;i<36;i++) {
                ItemStack stack=mc.player.getInventory().getStack(i);
                if (ItemStack.areItemsAndComponentsEqual(template,stack)) counts[i]=stack.getCount();
            }
            var plan=MossStackPlan.choose(counts,template.getMaxCount());
            if (plan==null) continue;
            if (!InventoryGuard.acquire(this)) return false;
            int sync=mc.player.playerScreenHandler.syncId;
            ItemStack source=mc.player.getInventory().getStack(plan.source()).copy();
            ItemStack destination=mc.player.getInventory().getStack(plan.destination()).copy();
            mc.interactionManager.clickSlot(sync,plan.source(),0,SlotActionType.PICKUP,mc.player);
            if (ItemStack.areEqual(mc.player.currentScreenHandler.getCursorStack(),source)
                && ItemStack.areEqual(mc.player.getInventory().getStack(plan.destination()),destination))
                mc.interactionManager.clickSlot(sync,plan.destination(),0,SlotActionType.PICKUP,mc.player);
            ItemStack cursor=mc.player.currentScreenHandler.getCursorStack();
            if (!cursor.isEmpty() && ItemStack.areItemsAndComponentsEqual(cursor,source)
                && mc.player.getInventory().getStack(plan.source()).isEmpty())
                mc.interactionManager.clickSlot(sync,plan.source(),0,SlotActionType.PICKUP,mc.player);
            world=mc.world;
            if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                fail("Inventory changed during moss consolidation; clear the cursor before restarting."); return true;
            }
            consolidating=true; replenishedSlot=-1;
            enter(State.HOTBAR_SETTLE); waitTicks=2;
            status="Combining partial moss stacks";
            return true;
        }
        return false;
    }

    private boolean refillCombatBusy() {
        var aura=Modules.get().get(meteordevelopment.meteorclient.systems.modules.combat.KillAura.class);
        var eat=Modules.get().get(meteordevelopment.meteorclient.systems.modules.player.AutoEat.class);
        var gap=Modules.get().get(meteordevelopment.meteorclient.systems.modules.player.AutoGap.class);
        var use=mc.player.getActiveItem().getUseAction();
        return (aura!=null && aura.isActive() && aura.attacking)
            || (eat!=null && eat.isActive() && eat.eating) || (gap!=null && gap.isEating())
            || (mc.player.isUsingItem() && (use==net.minecraft.item.consume.UseAction.EAT || use==net.minecraft.item.consume.UseAction.DRINK));
    }
    /** Return travel is orthogonal to transfers: Baritone's input can move the player with our shulker GUI open. */
    private boolean recoverPosition() {
        boolean eligible=switch (state) {
            case OPEN,WAIT_OPEN,TRANSFER,WAIT_TRANSFER,MERGE_CARRY,MERGE_RETURN,CLOSE,MINE -> true;
            default -> false;
        };
        if (!returnPosition.get() || anchor==null || !eligible) {
            if (returning) { stopBaritone(); returning=false; returnCommands.clear(); }
            return false;
        }
        var pos=mc.player.getPos();
        if (!returning && anchor.displaced(pos.x,pos.y,pos.z)) {
            returning=true; returnTicks=0; returnCommands.clear();
            mc.interactionManager.cancelBlockBreaking();
        }
        if (!returning) return false;
        if (mc.player.currentScreenHandler==mc.player.playerScreenHandler
            && (state==State.TRANSFER || state==State.WAIT_TRANSFER || state==State.MERGE_CARRY || state==State.MERGE_RETURN)) {
            // The server may close a container after knockback. Reopen and recount once back in reach.
            containerId=receivedInventoryId=-1; enter(State.OPEN);
        }
        if (anchor.contains(pos.x,pos.y,pos.z) && mc.player.isOnGround()) {
            stopBaritone(); returning=false; returnCommands.clear();
            if (state==State.MINE) selectTool();
            status="Returned to refill position"; return false;
        }
        if (++returnTicks>200) { fail("Could not return to the saved refill position within 10 seconds."); return true; }
        BlockPos goal=new BlockPos(anchor.blockX(),anchor.blockY(),anchor.blockZ());
        // Keep refill permissions: this return route may walk/jump, but cannot mine or build through the box.
        PathingSafety.acquire(this);
        if (returnCommands.canDispatch(mc.player.age,goal,10)) {
            NavigationTransport.go(goal); returnCommands.dispatched(mc.player.age,goal);
        }
        status="Returning to refill position";
        // Continue safe transfers while the owned container remains open. Defer reopening/mining until home.
        return !(ownContainer() && (state==State.TRANSFER || state==State.WAIT_TRANSFER
            || state==State.MERGE_CARRY || state==State.MERGE_RETURN));
    }

    private boolean ownContainer() {
        return mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler && mc.player.currentScreenHandler.syncId == containerId;
    }
    private void enter(State next) { state = next; stateTicks = 0; waitTicks = delay.get(); status = next.toString(); }
    private void stopBaritone() { NavigationTransport.stop(); }
    private int countBoxes() {
        int n = 0;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isOf(shulkerItem)) n += mc.player.getInventory().getStack(i).getCount();
        return n;
    }

    private BlockHitResult findPlacement(boolean lowerOnly) {
        BlockPos origin = mc.player.getBlockPos();
        BlockHitResult best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (int x = -3; x <= 3; x++) for (int y = -2; y <= (lowerOnly ? -1 : 2); y++) for (int z = -3; z <= 3; z++) {
            BlockPos cell = origin.add(x, y, z);
            if (rejectedPlaces.contains(cell) || !placementClear(cell)) continue;
            BlockPos support = cell.down();
            if (world.getBlockEntity(support) != null || !world.getBlockState(support).isSideSolidFullSquare(world, support, Direction.UP)) continue;
            int neighbors = 0;
            if (safeSupply.get()) {
                for (int nx = -1; nx <= 1; nx++) for (int nz = -1; nz <= 1; nz++) {
                    if (nx == 0 && nz == 0) continue;
                    BlockPos adjacent = support.add(nx, 0, nz);
                    if (world.isChunkLoaded(adjacent) && world.getBlockState(adjacent).getFluidState().isEmpty()
                        && world.getBlockState(adjacent).isSideSolidFullSquare(world, adjacent, Direction.UP)) neighbors++;
                }
                if (neighbors < supportNeighbors.get()) continue;
            }
            BlockHitResult hit = new BlockHitResult(Vec3d.ofBottomCenter(cell), Direction.UP, support, false);
            // Prefer a cappable location, but inability to cap must not prevent a refill.
            double d = mc.player.getEyePos().squaredDistanceTo(hit.getPos()) + (capEnabled() && !capReachable(cell) ? 1000 : 0);
            if (safeSupply.get()) d += (world.getBlockState(support).isOf(preferredSupport.get()) ? 0 : 100_000)
                + (8 - neighbors) * 10_000;
            if (d < distance && visible(hit)) { distance = d; best = hit; }
        }
        return best;
    }

    /** Only a preference: fallback placement does not require a cappable top from here. */
    private boolean capReachable(BlockPos cell) {
        Vec3d eye = mc.player.getEyePos();
        if (cell.getY() + 1 >= eye.y) return false;
        Vec3d top = Vec3d.ofBottomCenter(cell).add(0, 1.001, 0);
        double reach = Math.min(4.25, mc.player.getBlockInteractionRange() - .15);
        if (eye.squaredDistanceTo(top) > reach * reach) return false;
        // The box does not exist yet; the ray must reach its prospective top through air.
        return world.raycast(new RaycastContext(eye, top, RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    private boolean placementClear(BlockPos cell) {
        return RegionConstraint.allows(cell.getX(), cell.getY(), cell.getZ())
            && world.getWorldBorder().contains(cell) && world.getBlockState(cell).isAir() && world.getBlockState(cell.up()).isAir()
            && !mc.player.getBoundingBox().intersects(new Box(cell).stretch(0, 1, 0))
            && world.getOtherEntities(mc.player, new Box(cell).stretch(0, 1, 0), entity -> !entity.isSpectator()).isEmpty();
    }

    private BlockHitResult visibleFace(BlockPos pos) {
        for (Direction side : Direction.values()) {
            Vec3d point = Vec3d.ofCenter(pos).add(side.getOffsetX() * .5, side.getOffsetY() * .5, side.getOffsetZ() * .5);
            BlockHitResult hit = new BlockHitResult(point, side, pos, false);
            if (visible(hit)) return hit;
        }
        return null;
    }

    private boolean visible(BlockHitResult hit) {
        Vec3d eye = mc.player.getEyePos();
        double reach = Math.min(4.25, mc.player.getBlockInteractionRange() - .15);
        if (eye.squaredDistanceTo(hit.getPos()) > reach * reach) return false;
        Direction face = hit.getSide();
        Vec3d inside = hit.getPos().add(-face.getOffsetX() * .001, -face.getOffsetY() * .001, -face.getOffsetZ() * .001);
        BlockHitResult ray = world.raycast(new RaycastContext(eye, inside, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return ray.getType() == HitResult.Type.BLOCK && ray.getBlockPos().equals(hit.getBlockPos()) && ray.getSide() == face;
    }

    private void rotateInteract(BlockHitResult hit, State expected) {
        interactionSent = false;
        Rotations.rotate(Rotations.getYaw(hit.getPos()), Rotations.getPitch(hit.getPos()), 100, () -> {
            if (!isActive() || ActivityPause.isPaused() || AutomationContext.blockedScreen() || state != expected || mc.world != world || !visible(hit)) return;
            interactionSent = true;
            if (expected==State.WAIT_PLACE) {
                Vec3d pos=mc.player.getPos(); anchor=new RefillAnchor(pos.x,pos.y,pos.z);
                returning=false; returnCommands.reset();
            }
            if (expected == State.CAP_WAIT) {
                // Interaction bypass is sneak state, NOT BlockHitResult.insideBlock. Never touch the sneak key (Freecam).
                boolean previous = mc.player.isSneaking();
                var input = mc.player.input;
                var previousInput = input.playerInput;
                try {
                    if (!previous) mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
                    // ClientPlayerEntity reads this record for isSneaking; setting the entity flag alone is insufficient.
                    input.playerInput = new net.minecraft.util.PlayerInput(previousInput.forward(), previousInput.backward(),
                        previousInput.left(), previousInput.right(), previousInput.jump(), true, previousInput.sprint());
                    if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
                    else skipCap("Cap interaction was rejected.");
                } finally {
                    input.playerInput = previousInput;
                    if (!previous) mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
                }
            } else if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
        });
    }
    @Override public String getInfoString() { return status + (transactionActive() ? " | step " + stateTicks + "/" + (timeout.get()*20) + " ticks" : ""); }

    /** Use one refill block to seal an exhausted supply box before the placer can resume. */
    private void prepareCap() {
        if (!MossInventory.playerInventoryReady()) return;
        int source = -1;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isOf(Items.MOSS_BLOCK)) { source = i; break; }
        if (source < 0 && mc.player.getOffHandStack().isOf(Items.MOSS_BLOCK)) source = 45;
        if (source < 0) { skipCap("No moss available for a cap."); return; }
        capSlot = source < 9 ? source : MossInventory.safeHotbarSlot(hotbar);
        if (capSlot < 0) { skipCap("No non-sword hotbar slot available."); return; }
        if (source >= 9) mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
            MossInventory.playerSlotId(source), capSlot, SlotActionType.SWAP, mc.player);
        capConfirmed = false;
        enter(State.CAP_AIM);
    }
    private void placeCap() {
        if (!MossInventory.playerInventoryReady()) return;
        if (!capEnabled()) { skipCap("Capping disabled."); return; }
        if (world.getBlockState(placedPos.up()).isOf(net.minecraft.block.Blocks.MOSS_BLOCK)) { enter(State.FINISH); return; }
        if (!world.getBlockState(placedPos).isOf(placedBlock) || !world.getBlockState(placedPos.up()).isAir()) {
            skipCap("Shulker or its cap space changed."); return;
        }
        if (!mc.player.getMainHandStack().isOf(Items.MOSS_BLOCK)) { InvUtils.swap(capSlot, false); waitTicks = Math.max(2, delay.get()); return; }
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(placedPos).add(0, .5, 0), Direction.UP, placedPos, false);
        if (!visible(hit)) { routeGoal = null; enter(State.CAP_ROUTE); return; }
        enter(State.CAP_WAIT);
        rotateInteract(hit, State.CAP_WAIT);
    }

    /** Capping is best-effort: a failed cap never strands the completed refill. */
    private void skipCap(String reason) {
        capSkipped = true;
        stopBaritone(); PathingSafety.acquire(this); routeGoal = null;
        warning("%s Leaving the supply shulker at %s and finishing refill.", reason, placedPos.toShortString());
        enter(State.FINISH);
    }

    private void resetBoxAttempt() {
        placedPos = null; containerId = receivedInventoryId = -1;
        anchor=null; returning=false; returnCommands.reset();
        anywhere = false; capSkipped = false; recoverBox = false;
        placementAttempts = capAttempts = 0;
        rejectedPlaces.clear(); rejectedVantage.clear();
        routeGoal = null; pickupCommands.reset();
        pickupEntityId = -1; previousDrops.clear();
    }

    /** Retry only while the actual supply item is still in hand: never duplicate an uncertain placement. */
    private void retryPlacement() {
        if (placedPos != null && world.getBlockState(placedPos).isOf(placedBlock)
            && !mc.player.getInventory().getStack(hotbar).isOf(shulkerItem)) {
            boxBaseline = countBoxes(); enter(State.OPEN); return;
        }
        if (placedPos != null && world.getBlockState(placedPos).isOf(placedBlock)) {
            fail("A shulker is present but inventory consumption is unconfirmed. Check it before retrying."); return;
        }
        if (!mc.player.getInventory().getStack(hotbar).isOf(shulkerItem)) {
            fail("Supply item changed before placement was confirmed. Check the attempted position."); return;
        }
        if (placeHit != null) rejectedPlaces.add(placeHit.getBlockPos().up());
        if (++placementAttempts >= 4) { fail("Four reachable shulker placement attempts were rejected."); return; }
        anywhere = true; placedPos = null; serverPlaced = false;
        warning("Shulker placement failed; trying another reachable location.");
        enter(State.PREPARE);
    }

    private boolean canSeeTopFrom(BlockPos feet) {
        if (feet.getX() == placedPos.getX() && feet.getZ() == placedPos.getZ()) return false;
        if (!RegionConstraint.allows(feet.getX(), feet.getY(), feet.getZ()) || !world.isChunkLoaded(feet)
            || !world.getBlockState(feet).isAir() || !world.getBlockState(feet.up()).isAir()) return false;
        if (!world.getBlockState(feet.down()).isSideSolidFullSquare(world, feet.down(), Direction.UP)) return false;
        Vec3d eye = Vec3d.ofBottomCenter(feet).add(0, mc.player.getEyeHeight(net.minecraft.entity.EntityPose.STANDING), 0);
        Vec3d top = Vec3d.ofCenter(placedPos).add(0, .499, 0);
        double reach = Math.min(4.25, mc.player.getBlockInteractionRange() - .15);
        if (eye.y <= top.y || eye.squaredDistanceTo(top) > reach * reach) return false;
        BlockHitResult hit = world.raycast(new RaycastContext(eye, top, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(placedPos) && hit.getSide() == Direction.UP;
    }

    private void capRoute() {
        if (!capEnabled()) { skipCap("Capping disabled."); return; }
        if (!world.getBlockState(placedPos).isOf(placedBlock)) { skipCap("Supply shulker is no longer present."); return; }
        BlockHitResult top = new BlockHitResult(Vec3d.ofCenter(placedPos).add(0, .5, 0), Direction.UP, placedPos, false);
        if (mc.player.isOnGround() && visible(top) && !mc.player.getBoundingBox().intersects(new Box(placedPos.up()))) {
            stopBaritone(); PathingSafety.acquire(this); routeGoal = null; enter(State.CAP_SELECT); return;
        }
        if (routeGoal == null) {
            if (capAttempts >= 3) { skipCap("Could not reach a cap position."); return; }
            BlockPos best = null; double bestDistance = Double.POSITIVE_INFINITY;
            for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = -4; z <= 4; z++) {
                BlockPos feet = placedPos.add(x, y, z);
                if (rejectedVantage.contains(feet) || !canSeeTopFrom(feet)) continue;
                double distance = mc.player.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(feet));
                if (distance < bestDistance) { best = feet; bestDistance = distance; }
            }
            if (best == null) { skipCap("No visible reachable cap vantage point."); return; }
            capAttempts++;
            if (!routeTo(best)) { rejectedVantage.add(best); routeGoal = null; }
        } else if (!routeTo(routeGoal)) {
            rejectedVantage.add(routeGoal); stopBaritone(); routeGoal = null; pickupCommands.clear();
        }
    }

    /** Recovery paths may break/build, unlike the inventory-interaction phase of a refill. */
    private boolean routeTo(BlockPos goal) {
        LavacastPathfinder walker = Modules.get().get(LavacastPathfinder.class);
        PathingSafety.recovery(this, walker != null && walker.allowMossBreaking());
        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        double distance = mc.player.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(goal));
        if (!goal.equals(routeGoal)) {
            ensureRouteMoss();
            stopBaritone(); pickupCommands.clear();
            routeGoal = goal.toImmutable(); routeTicks = routeStall = 0; routeBest = distance;
            NavigationTransport.go(goal);
            return true;
        }
        routeTicks++;
        if (distance < routeBest - .25) { routeBest = distance; routeStall = 0; }
        else routeStall++;
        if (routeTicks > 200 || routeStall > 80) return false;
        if (routeTicks > 20 && !baritone.getCustomGoalProcess().isActive()
            && !baritone.getPathingBehavior().isPathing() && baritone.getPathingBehavior().getInProgress().isEmpty()) return false;
        return true;
    }

    private void ensureRouteMoss() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(Items.MOSS_BLOCK)) return;
        hotbar = MossInventory.safeHotbarSlot(hotbar);
        if (hotbar < 0) return;
        for (int i = 9; i < 36; i++) if (mc.player.getInventory().getStack(i).isOf(Items.MOSS_BLOCK)) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, hotbar, SlotActionType.SWAP, mc.player);
            return;
        }
    }

    private boolean beginMerge(int source) {
        ItemStack supply = mc.player.currentScreenHandler.getSlot(source).getStack();
        for (int slot = 27; slot < mc.player.currentScreenHandler.slots.size(); slot++) {
            ItemStack target = mc.player.currentScreenHandler.getSlot(slot).getStack();
            if (target.isEmpty() || target.getCount() >= target.getMaxCount()
                || !ItemStack.areItemsAndComponentsEqual(supply, target)) continue;
            if (lighting && !LightRefillTarget.mayFillPartial(supplyCount(),target.getCount(),target.getMaxCount())) continue;
            mergeSource = source; mergeDestination = slot;
            mergeStack = supply.copy(); mergeTarget = target.copy(); mossBeforeClick = supplyCount();
            mc.interactionManager.clickSlot(containerId, source, 0, SlotActionType.PICKUP, mc.player);
            enter(State.MERGE_CARRY); return true;
        }
        return false;
    }
    private void mergeIntoStack() {
        if (!ownContainer() || !ItemStack.areEqual(mc.player.currentScreenHandler.getCursorStack(), mergeStack)
            || !ItemStack.areEqual(mc.player.currentScreenHandler.getSlot(mergeDestination).getStack(), mergeTarget)) {
            fail("Partial-stack transfer changed; check the cursor before retrying."); return;
        }
        mc.interactionManager.clickSlot(containerId, mergeDestination, 0, SlotActionType.PICKUP, mc.player);
        enter(State.MERGE_RETURN);
    }
    private void returnRemainder() {
        if (!ownContainer()) { fail("Shulker closed during partial-stack transfer."); return; }
        ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
        if (!cursor.isEmpty()) {
            if (!ItemStack.areItemsAndComponentsEqual(cursor, mergeStack)
                || !mc.player.currentScreenHandler.getSlot(mergeSource).getStack().isEmpty()) {
                fail("Cannot return remaining moss; check the cursor."); return;
            }
            mc.interactionManager.clickSlot(containerId, mergeSource, 0, SlotActionType.PICKUP, mc.player);
        }
        enter(State.WAIT_TRANSFER);
    }
}
