package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import java.util.List;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Keeps supply boxes away from placement modules; refill owns its temporary hotbar box. */
public final class ShulkerHotbarGuard {
    private static final Object OWNER = new Object(), PAUSE = new Object();
    private static int settle;
    private static boolean warned;

    static boolean enabled() {
        LavacastPathfinder walker = Modules.get().get(LavacastPathfinder.class);
        MossRefill refill = Modules.get().get(MossRefill.class);
        return mc.player != null && walker != null && walker.keepShulkersOutOfHotbar()
            && (walker.isActive() || (refill != null && refill.isActive()));
    }

    private static int source() {
        for (int i = 0; i < 9; i++) if (MossInventory.isShulker(mc.player.getInventory().getStack(i))) return i;
        return -1;
    }

    static boolean busy() { return enabled() && (settle > 0 || source() >= 0); }

    // Take over the placer's pause BEFORE refill releases its own inventory lease.
    static void beforeRefillRelease() {
        if (busy()) ModulePauses.hold(PAUSE, List.of("moss-placer", "auto-replenish"));
    }

    public static boolean blockPlacement(Hand hand) {
        if (!enabled() || !MossInventory.isShulker(mc.player.getStackInHand(hand))) return false;
        MossRefill refill = Modules.get().get(MossRefill.class);
        return refill == null || !refill.placingSupplyBox();
    }

    @EventHandler(priority = 200) private void tick(TickEvent.Pre event) {
        if (!enabled()) { release(); warned = false; return; }
        MossRefill refill = Modules.get().get(MossRefill.class);
        // Do not compete with an active refill's selected supply or recovery actions.
        if (refill != null && refill.transactionActive()) return;
        if (settle > 0) { InventoryGuard.maintain(OWNER); settle--; return; }
        InventoryGuard.release(OWNER);
        int source = source();
        if (source < 0) { release(); warned = false; return; }
        beforeRefillRelease();
        if (!MossInventory.playerInventoryReady() || ActivityPause.isPaused()) return;
        int destination = -1;
        // Empty storage first; otherwise swap a non-shulker into the hotbar without dropping anything.
        for (int i = 9; i < 36; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) { destination = i; break; }
            if (destination < 0 && !MossInventory.isShulker(stack)) destination = i;
        }
        if (destination < 0) {
            if (!warned) Modules.get().get(LavacastPathfinder.class).warning("Cannot store hotbar shulkers. Free a main-inventory slot or disable Keep Shulkers Out of Hotbar.");
            warned = true;
            return;
        }
        if (!InventoryGuard.acquire(OWNER)) return;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, destination, source, SlotActionType.SWAP, mc.player);
        settle = 6; // Let the server settle each swap before continuing or resuming placement.
    }

    private static void release() {
        settle = 0;
        InventoryGuard.release(OWNER);
        ModulePauses.release(PAUSE);
    }
}
