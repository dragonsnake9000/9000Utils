package dev.dragonsnake9000.utils;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import static meteordevelopment.meteorclient.MeteorClient.mc;

final class MossInventory {
    static int countMoss() {
        if (mc.player == null) return 0;
        int count = mc.player.getOffHandStack().isOf(Items.MOSS_BLOCK) ? mc.player.getOffHandStack().getCount() : 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.MOSS_BLOCK)) count += s.getCount();
        }
        return count;
    }

    static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock;
    }

    static int mossInBox(ItemStack stack) {
        if (!isShulker(stack)) return 0;
        ContainerComponent contents = stack.get(DataComponentTypes.CONTAINER);
        int count = 0;
        if (contents != null) for (ItemStack inner : contents.iterateNonEmpty())
            if (inner.isOf(Items.MOSS_BLOCK)) count += inner.getCount();
        return count;
    }
    static boolean emptyShulker(ItemStack stack) {
        if (!isShulker(stack)) return false;
        ContainerComponent contents = stack.get(DataComponentTypes.CONTAINER);
        return contents == null || !contents.iterateNonEmpty().iterator().hasNext();
    }
    /** Use the least-filled moss supply first, including an offhand supply box. */
    static int findMossShulker() {
        int best = -1, least = Integer.MAX_VALUE;
        for (int slot = 0; slot <= 40; slot++) {
            if (slot >= 36 && slot != 40) continue;
            int moss = mossInBox(mc.player.getInventory().getStack(slot));
            if (moss > 0 && moss < least) { best = slot; least = moss; }
        }
        return best;
    }
    static int emptySlots() {
        int count = 0;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isEmpty()) count++;
        return count;
    }

    static int mergeSpace() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            // Standard moss only; never assume stacks with custom components will merge.
            if (ItemStack.areItemsAndComponentsEqual(stack, Items.MOSS_BLOCK.getDefaultStack())) count += stack.getMaxCount() - stack.getCount();
        }
        return count;
    }

    static int playerSlotId(int index) { return index == 40 ? 45 : index < 9 ? 36 + index : index; }
    static boolean playerInventoryReady() {
        return mc.player != null && mc.interactionManager != null && !AutomationContext.blockedScreen()
            && mc.player.currentScreenHandler == mc.player.playerScreenHandler
            && mc.player.currentScreenHandler.getCursorStack().isEmpty() && !mc.player.isUsingItem();
    }
}
