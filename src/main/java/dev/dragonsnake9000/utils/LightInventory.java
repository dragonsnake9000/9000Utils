package dev.dragonsnake9000.utils;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Only count items which emit light without an additional ignition or power source. */
final class LightInventory {
    static int strength(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return 0;
        var block = item.getBlock();
        // Do not make a walking roof hazardous in the name of lighting it.
        if (block == Blocks.SEA_PICKLE || block == Blocks.MAGMA_BLOCK || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) return 0;
        return block.getDefaultState().getLuminance();
    }
    static boolean isLight(ItemStack stack) { return strength(stack) >= RoofLighting.requiredSupplyStrength(); }
    static int count() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (isLight(stack)) count += stack.getCount();
        }
        return count;
    }
    static int findBox() {
        int best = -1, least = Integer.MAX_VALUE;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!MossInventory.isShulker(stack)) continue;
            var contents = stack.get(DataComponentTypes.CONTAINER);
            int count = 0;
            if (contents != null) for (var inner : contents.iterateNonEmpty()) if (isLight(inner)) count += inner.getCount();
            if (count > 0 && count < least) { best = i; least = count; }
        }
        return best;
    }
}
