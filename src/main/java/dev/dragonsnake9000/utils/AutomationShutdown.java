package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.sound.SoundEvents;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Exhaustion is a terminal stop, not an endless wait or an inventory-restore restart. */
final class AutomationShutdown {
    private static boolean stopping;
    static void noMoss() {
        if (stopping) return;
        stopping = true;
        LavacastPathfinder walker = Modules.get().get(LavacastPathfinder.class);
        boolean ping = walker != null && walker.supplyPing();
        try {
            ModulePauses.suppressRestore("moss-placer");
            for (Class<? extends Module> type : java.util.List.of(LavacastPathfinder.class, MossRefill.class, ArmorRefresh.class, MossInventoryRefresh.class)) {
                Module module = Modules.get().get(type);
                if (module != null && module.isActive()) module.toggle();
            }
            Module placer = Modules.get().get("moss-placer");
            if (placer != null && placer.isActive()) placer.toggle();
            NavigationTransport.stop();
            ChatUtils.warningPrefix("9000Utils", "No moss detected in inventory or supply shulkers. Automation stopped.");
            if (ping && mc.player != null) mc.player.playSound(SoundEvents.ENTITY_ARROW_HIT_PLAYER, 1f, 1f);
        } finally { stopping = false; }
    }
}
