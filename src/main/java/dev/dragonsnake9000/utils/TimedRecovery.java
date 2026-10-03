package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

abstract class TimedRecovery extends Module {
    protected final SettingGroup general = settings.getDefaultGroup();
    private final Setting<Integer> interval;
    private final Setting<Boolean> onlyWalking = general.add(new BoolSetting.Builder().name("only-while-walker-enabled")
        .description("Only run while Lavacast Pathfinder is enabled.").defaultValue(true).build());
    protected final Setting<Integer> settle = general.add(new IntSetting.Builder().name("action-delay-ticks")
        .description("Spacing between inventory actions; zero still separates actions across ticks.").defaultValue(4).range(0, 1200).sliderRange(0, 40).build());
    private int timer, busyTicks;
    private boolean busy;

    TimedRecovery(String name, String description, int defaultInterval) {
        super(Utils9000Addon.CATEGORY, name, description);
        interval = general.add(new IntSetting.Builder().name("interval-seconds")
            .description("Time between recovery attempts, at 20 game ticks per second.").defaultValue(defaultInterval).range(1, 86400).sliderRange(1, 120).build());
        runInMainMenu = true;
    }
    @Override public void onActivate() { timer = interval.get() * 20; busy = false; }
    @Override public void onDeactivate() { cleanup(); InventoryGuard.release(this); busy = false; }
    @EventHandler private void onLeave(GameLeftEvent event) { if (isActive()) toggle(); }

    @EventHandler(priority = -100) private void tick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.world == null) return;
        if (!mc.player.isAlive()) { if (isActive()) toggle(); return; }
        if (ActivityPause.isPaused()) return;
        if (busy) {
            InventoryGuard.maintain(this);
            if (++busyTicks == Math.max(1, settle.get())) cleanup();
            if (busyTicks >= Math.max(1, settle.get()) * 2) {
                InventoryGuard.release(this);
                busy = false;
                timer = interval.get() * 20;
            }
            return;
        }
        if (timer > 0) { timer--; return; }
        LavacastPathfinder walker = Modules.get().get(LavacastPathfinder.class);
        MossRefill refill = Modules.get().get(MossRefill.class);
        if (onlyWalking.get() && (walker == null || !walker.isActive())) return;
        if (refill != null && refill.shouldPauseWalking()) return;
        if (!MossInventory.playerInventoryReady() || !mc.player.isOnGround()) return;
        if (!InventoryGuard.acquire(this)) return;
        busy = perform();
        busyTicks = 0;
        if (!busy) { InventoryGuard.release(this); timer = interval.get() * 20; }
    }

    protected abstract boolean perform();
    protected abstract void cleanup();
}
