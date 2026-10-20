package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import java.util.*;

/** Optional conservative warning/disconnect module. Client observations cannot establish intent. */
public final class AntiMace extends Module {
    private final DebugView debug = new DebugView(this,settings,() -> "Tracking " + this.tracks.size() + " players | aim is supporting evidence only",this::drawDebug);
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<Boolean> alertOnly = general.add(new BoolSetting.Builder().name("alert-only")
        .description("Warn in chat instead of disconnecting.").defaultValue(false).build());
    private final Setting<Boolean> friends = general.add(new BoolSetting.Builder().name("ignore-friends").defaultValue(true).build());
    private final Setting<Boolean> rusherFriends = general.add(new BoolSetting.Builder().name("ignore-rusherhack-friends")
        .description("Ignore RusherHack friends through its optional public API. Does not require RusherHack.").defaultValue(true).build());
    private final Setting<Boolean> pivots = general.add(new BoolSetting.Builder().name("detect-upward-dive-reversal")
        .description("Measure descent from a rapid upward-flight reversal, avoiding ascent masking the dive. Still requires approach, descent and confirmation checks; held mace is supporting evidence only.").defaultValue(true).build());
    private final RusherHackFriends rusher = new RusherHackFriends();
    private final Setting<Boolean> flight = general.add(new BoolSetting.Builder().name("require-flight-evidence")
        .description("Also require recent observed elytra flight or a chestplate swap during rapid airborne descent. A swap alone never triggers.").defaultValue(true).build());
    private final Setting<Double> speed = decimal("minimum-descent-speed", "Observed downward blocks per tick.", 0.6, .1, 10);
    private final Setting<Double> drop = decimal("minimum-observed-drop", "Minimum Y loss within eight ticks before considering a threat.", 4.0, 1, 40);
    private final Setting<Double> radius = decimal("predicted-pass-radius", "Maximum predicted horizontal distance at striking height.", 2.5, .5, 6);
    private final Setting<Double> horizon = decimal("prediction-ticks", "Only react when predicted striking height is this many ticks away or less.", 6.0, 1, 20);
    private final Setting<Integer> confirm = general.add(new IntSetting.Builder().name("confirmation-ticks")
        .description("Consecutive qualifying observations required.").defaultValue(2).range(1, 10).sliderRange(1, 5).build());
    private final Map<UUID, MaceThreatTracker> tracks = new HashMap<>();
    private Object world;
    private int tick, lastAlert = -1000;
    public AntiMace() { super(Utils9000Addon.CATEGORY, "anti-mace", "Detects possible incoming mace dives and disconnects, or warns in alert-only mode."); }
    private void drawDebug(meteordevelopment.meteorclient.events.render.Render3DEvent event) {
        var own=mc.player.getPos();
        debug.circle(event,own.x,own.y+1.5,own.z,radius.get(),debug.line(255,70,70));
        int count=0;
        for (var player:mc.world.getPlayers()) {
            if (!tracks.containsKey(player.getUuid()) || count++>=16) continue;
            var look=net.minecraft.util.math.Vec3d.fromPolar(player.getPitch(),player.getHeadYaw());
            var toward=mc.player.getBoundingBox().getCenter().subtract(player.getEyePos());
            boolean aimed=AimEvidence.supports(AimEvidence.angle(look.x,look.y,look.z,toward.x,toward.y,toward.z),player.canSee(mc.player));
            debug.cone(event,player.getEyePos(),look,Math.min(48,toward.length()+2),15,aimed ? debug.line(255,70,70) : debug.line(255,190,60));
        }
    }
    private Setting<Double> decimal(String name, String description, double value, double min, double max) {
        return general.add(new DoubleSetting.Builder().name(name).description(description).defaultValue(value).range(min, max).sliderRange(min, max).build());
    }
    private void reset() { tracks.clear(); tick = 0; lastAlert = -1000; world = mc.world; }
    @Override public void onActivate() { reset(); }
    @Override public void onDeactivate() { reset(); }
    @EventHandler private void leave(GameLeftEvent event) { reset(); }
    @EventHandler(priority = 1000) private void tick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null || !mc.player.isAlive()) return;
        if (world != mc.world) reset();
        tick++;
        var rules = new MaceThreatTracker.Rules(speed.get(), drop.get(), radius.get(), horizon.get(), flight.get(), confirm.get(), pivots.get());
        Set<UUID> visible = new HashSet<>();
        for (var other : mc.world.getPlayers()) {
            if (other == mc.player || other.getUuid().equals(mc.player.getUuid()) || !other.isAlive() || other.isSpectator()
                || (friends.get() && Friends.get().isFriend(other)) || other.squaredDistanceTo(mc.player) > 96 * 96
                || (rusherFriends.get() && rusher.isFriend(other.getGameProfile().getName(), message -> warning("%s", message)))) continue;
            UUID id = other.getUuid(); visible.add(id);
            var chest = other.getEquippedStack(EquipmentSlot.CHEST);
            boolean chestplate = Set.of(Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE,
                Items.GOLDEN_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE).contains(chest.getItem());
            boolean threat = tracks.computeIfAbsent(id, ignored -> new MaceThreatTracker()).update(
                new MaceThreatTracker.Sample(tick, other.getX(), other.getY(), other.getZ()), other.isOnGround(),
                chest.isOf(Items.ELYTRA), chestplate, other.isGliding(), other.getMainHandStack().isOf(Items.MACE),
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getVelocity().x,
                mc.player.getVelocity().y, mc.player.getVelocity().z, rules);
            if (!threat) continue;
            // Remote head rotation is supporting evidence, not an additional trigger requirement.
            // Compare their observed look direction to the center of our actual player's body.
            var look = net.minecraft.util.math.Vec3d.fromPolar(other.getPitch(), other.getHeadYaw());
            var toward = mc.player.getBoundingBox().getCenter().subtract(other.getEyePos());
            double aimAngle = AimEvidence.angle(look.x, look.y, look.z, toward.x, toward.y, toward.z);
            boolean aimed = AimEvidence.supports(aimAngle, other.canSee(mc.player));
            String aim = aimed
                ? String.format(Locale.ROOT, "\n- Aim toward you observed (%.1f degrees from your body center)", aimAngle)
                : "\n- Aim toward you not confirmed (not required)";
            String reason = "Account: " + mc.player.getGameProfile().getName()
                + "\n9000Utils: Detected a possible mace attempt from " + other.getGameProfile().getName()
                + String.format(Locale.ROOT, "\nLogout coordinates: X %.2f, Y %.2f, Z %.2f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
                + "\nDimension: " + mc.world.getRegistryKey().getValue()
                + "\nDate/time: " + java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT))
                + tracks.get(id).reasons() + aim;
            if (alertOnly.get()) {
                if (tick - lastAlert >= 100) { warning("%s", reason); lastAlert = tick; }
            } else {
                // Disable before disconnect so automatic reconnect cannot repeatedly re-trigger this module.
                toggle();
                mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
                return;
            }
        }
        tracks.keySet().retainAll(visible); // Never carry stale motion across leaving/re-entering render distance.
    }
}
