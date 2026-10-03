package dev.dragonsnake9000.utils;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.consume.UseAction;
import java.util.List;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Runs after Meteor's aura/eating handlers, before our movement/inventory handlers. */
public final class ActivityPause {
    private static final Object OWNER = new Object();
    private static boolean paused;
    private int quietUntil;
    private Object world;
    private boolean inventoryHeld, previousInventory;
    public static boolean isPaused() { return paused || eatingPauseActive(); }
    public static boolean eatingPauseActive() {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (walker == null || !walker.pauseEating() || !automationEnabled() || mc.player == null) return false;
        var eat = Modules.get().get(AutoEat.class);
        var gap = Modules.get().get(AutoGap.class);
        UseAction use = mc.player.getActiveItem().getUseAction();
        return (mc.player.isUsingItem() && (use == UseAction.EAT || use == UseAction.DRINK))
            || (eat != null && eat.isActive() && eat.eating) || (gap != null && gap.isEating());
    }
    private static boolean automationEnabled() {
        return AutomationContext.active();
    }
    // Direct placement packets may run outside TickEvent. Lock automation as soon as food use starts.
    @EventHandler(priority = 2000) private void packet(meteordevelopment.meteorclient.events.packets.PacketEvent.Send event) {
        if (!automationEnabled() || mc.player == null) return;
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (walker == null || !walker.pauseEating()) return;
        if (event.packet instanceof net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket packet) {
            UseAction use = mc.player.getStackInHand(packet.getHand()).getUseAction();
            if (use == UseAction.EAT || use == UseAction.DRINK) {
                paused = true;
                quietUntil = mc.player.age + Math.max(2, walker.resumeDelay());
                hold();
            }
        } else if (isPaused() && event.packet instanceof net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket) {
            event.cancel();
        }
    }
    @EventHandler(priority = 2000) private void earlyTick(TickEvent.Pre event) {
        if (eatingPauseActive()) {
            paused = true;
            var walker = Modules.get().get(LavacastPathfinder.class);
            quietUntil = mc.player.age + walker.resumeDelay();
            hold();
        }
    }
    private void hold() {
        var engine = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
        if (!inventoryHeld) {
            // Cancel calculations and execution immediately, even mid-bridge; the pause process
            // prevents competing processes from restarting work until activity finishes.
            boolean using = mc.options.useKey.isPressed();
            engine.getPathingBehavior().cancelEverything();
            engine.getPathingBehavior().forceCancel();
            mc.options.useKey.setPressed(using);
            if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        }
        engine.getInputOverrideHandler().clearAllKeys();
        ModulePauses.hold(OWNER, List.of("moss-placer", "auto-replenish", "chest-swap"));
        if (!inventoryHeld) {
            previousInventory = baritone.api.BaritoneAPI.getSettings().allowInventory.value;
            inventoryHeld = true;
        }
        baritone.api.BaritoneAPI.getSettings().allowInventory.value = false;
    }
    private void release() {
        ModulePauses.release(OWNER);
        if (inventoryHeld) baritone.api.BaritoneAPI.getSettings().allowInventory.value = previousInventory;
        inventoryHeld = false;
    }
    @EventHandler(priority = -50) private void tick(TickEvent.Pre event) {
        LavacastPathfinder walker = Modules.get().get(LavacastPathfinder.class);
        boolean enabled = walker != null && (walker.isActive()
            || Modules.get().get(MossRefill.class).isActive()
            || Modules.get().get(ArmorRefresh.class).isActive()
            || Modules.get().get(MossInventoryRefresh.class).isActive());
        if (!enabled || mc.player == null || mc.world == null || !mc.player.isAlive()) { reset(); return; }
        if (world != mc.world) { reset(); world = mc.world; }
        KillAura aura = Modules.get().get(KillAura.class);
        AutoEat eat = Modules.get().get(AutoEat.class);
        AutoGap gap = Modules.get().get(AutoGap.class);
        UseAction use = mc.player.getActiveItem().getUseAction();
        boolean eating = (mc.player.isUsingItem() && (use == UseAction.EAT || use == UseAction.DRINK))
            || (eat != null && eat.isActive() && eat.eating) || (gap != null && gap.isEating());
        boolean combat = aura != null && aura.isActive() && aura.attacking;
        boolean busy = (walker.pauseEating() && eating) || (walker.pauseCombat() && combat);
        if (busy) quietUntil = mc.player.age + walker.resumeDelay();
        paused = busy || mc.player.age < quietUntil;
        if (paused) hold();
        else release();
    }
    @EventHandler private void leave(GameLeftEvent event) { reset(); }
    private void reset() { paused = false; quietUntil = 0; world = null; release(); }
}
