package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Paired number-key swaps never pick up a stack onto the inventory cursor. */
final class GappleInventoryRefresh {
    private final EatingEpisode episode = new EatingEpisode();
    private Object world;
    @EventHandler(priority = 1450) private void tick(TickEvent.Pre event) {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || mc.world == null || walker == null || !walker.isActive()
            || !walker.gappleInventoryRefresh() || !walker.pauseEating() || !mc.player.isAlive()) {
            episode.reset(); world = null; return;
        }
        if (world != mc.world) { episode.reset(); world = mc.world; }
        if (!episode.ready(ActivityPause.eatingPauseActive())) return;
        int hotbar = mc.player.getInventory().selectedSlot;
        var apple = mc.player.getInventory().getStack(hotbar);
        if (!apple.isOf(Items.GOLDEN_APPLE) && !apple.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return;
        // Never interrupt an offhand meal or a container/cursor transaction.
        if (mc.player.isUsingItem() && mc.player.getActiveHand() != net.minecraft.util.Hand.MAIN_HAND) return;
        if (mc.currentScreen != null || mc.interactionManager == null
            || mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.playerScreenHandler.getCursorStack().isEmpty()) return;
        var refill = Modules.get().get(MossRefill.class);
        if (refill != null && refill.transactionActive()) return;
        int empty = -1;
        for (int slot = 9; slot < 36; slot++) if (mc.player.getInventory().getStack(slot).isEmpty()) { empty = slot; break; }
        if (empty < 0) {
            episode.handled();
            walker.warning("Gapple inventory refresh skipped: no empty main-inventory slot.");
            return;
        }
        if (!InventoryGuard.acquire(this)) return;
        episode.handled();
        ItemStack original = apple.copy();
        try {
            walker.pauseForRefill();
            // Both clicks execute together; AutoEat cannot run between the outbound/return swaps.
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, empty, hotbar, SlotActionType.SWAP, mc.player);
            if (mc.player.getInventory().getStack(hotbar).isEmpty()
                && ItemStack.areEqual(mc.player.getInventory().getStack(empty), original)) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, empty, hotbar, SlotActionType.SWAP, mc.player);
            } else walker.warning("Gapple refresh interrupted by an inventory change; no unrelated items were swapped back.");
        } finally { InventoryGuard.release(this); }
    }
}
