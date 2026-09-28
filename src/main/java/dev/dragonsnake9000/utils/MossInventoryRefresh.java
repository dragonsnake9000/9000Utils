package dev.dragonsnake9000.utils;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public final class MossInventoryRefresh extends TimedRecovery {
    private int inventorySlot = -1, hotbarSlot;
    private ItemStack originalInventory, originalHotbar;

    public MossInventoryRefresh() {
        super("moss-inventory-refresh", "Periodically swaps moss between inventory slots and back to refresh placement.");
    }

    @Override protected boolean perform() {
        int moss = -1;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isOf(Items.MOSS_BLOCK)) { moss = i; break; }
        if (moss < 0) return false;
        if (moss < 9) {
            hotbarSlot = moss;
            inventorySlot = -1;
            for (int i = 9; i < 36; i++) if (!mc.player.getInventory().getStack(i).isOf(Items.MOSS_BLOCK)
                && (!ShulkerHotbarGuard.enabled() || !MossInventory.isShulker(mc.player.getInventory().getStack(i)))) {
                inventorySlot = i;
                if (mc.player.getInventory().getStack(i).isEmpty()) break;
            }
        } else {
            inventorySlot = moss;
            hotbarSlot = -1;
            for (int i = 0; i < 9; i++) if (!mc.player.getInventory().getStack(i).isOf(Items.MOSS_BLOCK)
                && (!ShulkerHotbarGuard.enabled() || !MossInventory.isShulker(mc.player.getInventory().getStack(i)))) {
                hotbarSlot = i;
                if (mc.player.getInventory().getStack(i).isEmpty()) break;
            }
        }
        if (inventorySlot < 0 || hotbarSlot < 0) { inventorySlot = -1; return false; }
        originalInventory = mc.player.getInventory().getStack(inventorySlot).copy();
        originalHotbar = mc.player.getInventory().getStack(hotbarSlot).copy();
        swap();
        return true;
    }

    private void swap() {
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, MossInventory.playerSlotId(inventorySlot),
            hotbarSlot, SlotActionType.SWAP, mc.player);
    }

    @Override protected void cleanup() {
        if (inventorySlot < 0) return;
        if (MossInventory.playerInventoryReady()
            && ItemStack.areEqual(mc.player.getInventory().getStack(inventorySlot), originalHotbar)
            && ItemStack.areEqual(mc.player.getInventory().getStack(hotbarSlot), originalInventory)) swap();
        // If the user/server changed either slot, never blindly swap unrelated items.
        inventorySlot = -1;
    }
}
