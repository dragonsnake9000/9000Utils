package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.consume.UseAction;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Reproduce the user's inventory-open/close workaround without clicking or moving any items. */
final class EatingInventoryRecovery {
    private final EatingRecoveryTimer timer = new EatingRecoveryTimer();
    private InventoryScreen opened;
    private final EatingInventoryHold hold = new EatingInventoryHold();
    private Object world;
    @EventHandler(priority = 1500) private void tick(TickEvent.Pre event) {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || mc.world == null || walker == null || !walker.isActive()
            || !walker.eatingInventoryRecovery() || !mc.player.isAlive() || (world != null && world != mc.world)) {
            if (opened != null && mc.currentScreen == opened) mc.setScreen(null);
            opened = null; world = null; timer.reset(); return;
        }
        world = mc.world;
        if (opened != null) {
            // Never close a different screen opened by the user or server during recovery.
            if (mc.currentScreen != opened) { opened = null; return; }
            if (hold.shouldClose(walker.eatingKeepOpen(), ActivityPause.eatingPauseActive())) {
                mc.setScreen(null); opened = null;
            }
            return;
        }
        if (mc.currentScreen != null || mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        int food = 0;
        for (int slot = 0; slot <= 40; slot++) {
            if (slot >= 36 && slot != 40) continue;
            var stack = mc.player.getInventory().getStack(slot);
            if (stack.getUseAction() == UseAction.EAT || stack.getUseAction() == UseAction.DRINK) food += stack.getCount();
        }
        if (!timer.update(ActivityPause.eatingPauseActive(), food)) return;
        walker.pauseForRefill();
        opened = new InventoryScreen(mc.player);
        hold.start(walker.eatingOpenTicks());
        mc.setScreen(opened);
        walker.info("Eating has not consumed food for five seconds. Refreshing inventory (%s/3).", timer.attempts());
    }
}
