package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class ArmorRefresh extends TimedRecovery {
    private int previousSlot = -1;
    public ArmorRefresh() {
        super("moss-armor-refresh", "Periodically uses hotbar armor or elytra to refresh moss placement. Changes equipped gear.");
    }
    @Override protected boolean perform() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            EquippableComponent equip = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equip == null || !isArmor(equip.slot())) continue;
            if (ItemStack.areItemsAndComponentsEqual(stack, mc.player.getEquippedStack(equip.slot()))) continue;
            previousSlot = mc.player.getInventory().selectedSlot;
            InvUtils.swap(i, false);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }
    private boolean isArmor(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }
    @Override protected void cleanup() {
        if (previousSlot >= 0 && mc.player != null && mc.interactionManager != null) InvUtils.swap(previousSlot, false);
        previousSlot = -1;
    }
}
