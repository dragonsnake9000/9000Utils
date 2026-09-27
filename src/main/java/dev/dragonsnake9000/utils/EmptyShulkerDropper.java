package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.screen.slot.SlotActionType;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Drops only truly empty boxes, only while the pathfinder explicitly enables this option. */
final class EmptyShulkerDropper {
    private static final Object OWNER = new Object();
    private static int wait;
    private static boolean locked;
    static void reset() { InventoryGuard.release(OWNER); locked = false; wait = 0; }
    @EventHandler(priority = -105) private void tick(TickEvent.Pre event) {
        LavacastPathfinder walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || walker == null || !walker.isActive() || !walker.dropEmptyShulkers()) { reset(); return; }
        if (locked) {
            InventoryGuard.maintain(OWNER);
            if (--wait <= 0) { InventoryGuard.release(OWNER); locked = false; wait = 10; }
            return;
        }
        if (wait-- > 0 || ActivityPause.isPaused() || !MossInventory.playerInventoryReady()) return;
        MossRefill refill = Modules.get().get(MossRefill.class);
        if (refill != null && refill.shouldPauseWalking()) return;
        for (int i = 0; i <= 40; i++) {
            if (i >= 36 && i != 40) continue;
            if (!MossInventory.emptyShulker(mc.player.getInventory().getStack(i))) continue;
            if (!InventoryGuard.acquire(OWNER)) return;
            locked = true; wait = 10;
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, MossInventory.playerSlotId(i), 1, SlotActionType.THROW, mc.player);
            return;
        }
        wait = 20;
    }
}
