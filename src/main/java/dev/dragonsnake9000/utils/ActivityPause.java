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
final class ActivityPause {
    private static final Object OWNER = new Object();
    private static boolean paused;
    private int quietUntil;
    private Object world;
    static boolean isPaused() { return paused; }
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
        if (paused) ModulePauses.hold(OWNER, List.of("moss-placer"));
        else ModulePauses.release(OWNER);
    }
    @EventHandler private void leave(GameLeftEvent event) { reset(); }
    private void reset() { paused = false; quietUntil = 0; world = null; ModulePauses.release(OWNER); }
}
