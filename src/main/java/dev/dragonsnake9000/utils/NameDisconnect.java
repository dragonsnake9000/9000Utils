package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.registry.tag.DamageTypeTags;

/** Checks loaded world entities, never merely the server-wide tab list. */
public final class NameDisconnect extends Module {
    enum Detection { Render_Distance, Line_Of_Sight }
    enum Weapons { Sword, Mace, Both }
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<List<String>> names = general.add(new StringListSetting.Builder().name("player-names")
        .description("Exact Minecraft account names, case-insensitive. Listed names override friend status.").defaultValue(List.of()).build());
    private final Setting<Detection> detection = general.add(new EnumSetting.Builder<Detection>().name("detection")
        .description("Render Distance checks all loaded players, even behind walls. Line Of Sight additionally requires visibility from your player.").defaultValue(Detection.Render_Distance).build());
    private final Setting<Boolean> learnAttackers = general.add(new BoolSetting.Builder().name("auto-add-attackers")
        .description("While this module is active, add players who damage you with a selected weapon. Added names follow the normal disconnect rules, including friends.")
        .defaultValue(false).build());
    private final Setting<Weapons> weapons = general.add(new EnumSetting.Builder<Weapons>().name("attacker-weapons")
        .description("Which melee weapons trigger automatic name additions. Ordinary hits use the attacker's observed main-hand item.")
        .defaultValue(Weapons.Both).visible(learnAttackers::get).build());
    private final Setting<Boolean> learnCrystals = general.add(new BoolSetting.Builder().name("auto-add-crystal-attackers")
        .description("Add a player when server-attributed end crystal damage hits you. Independent of melee learning. Unattributed explosions are ignored; listed names override friends.")
        .defaultValue(false).build());
    private final Map<Integer, Integer> removedCrystals = new HashMap<>();
    private Object crystalWorld;
    @Override public void onActivate() { removedCrystals.clear(); crystalWorld = mc.world; }
    @Override public void onDeactivate() { removedCrystals.clear(); crystalWorld = null; }
    private void refreshCrystals() {
        if (crystalWorld != mc.world) { removedCrystals.clear(); crystalWorld = mc.world; }
        if (mc.player != null) removedCrystals.values().removeIf(until -> until < mc.player.age);
        if (!learnCrystals.get()) removedCrystals.clear();
    }
    /** Called before removal, so crystal identity survives packet ordering without guessing. */
    public void rememberRemovedCrystal(int id) {
        refreshCrystals();
        if (isActive() && learnCrystals.get() && mc.player != null && mc.world != null
            && mc.world.getEntityById(id) instanceof EndCrystalEntity)
            removedCrystals.put(id, mc.player.age + 20);
    }
    public NameDisconnect() { super(Utils9000Addon.CATEGORY, "name-disconnect", "Disconnects when a listed player is loaded nearby or visible."); }

    /** Called on the client thread after vanilla handles a server damage notification. */
    public void observeDamage(EntityDamageS2CPacket packet) {
        if (!isActive() || (!learnAttackers.get() && !learnCrystals.get()) || mc.player == null || mc.world == null
            || packet.entityId() != mc.player.getId()) return;
        refreshCrystals();
        var damage = packet.createDamageSource(mc.world);
        if (!(damage.getAttacker() instanceof PlayerEntity attacker)
            || attacker.getUuid().equals(mc.player.getUuid())) return;
        var direct = mc.world.getEntityById(packet.sourceDirectId());
        boolean crystal = direct instanceof EndCrystalEntity
            || (direct == null && removedCrystals.containsKey(packet.sourceDirectId()));
        if (learnCrystals.get() && damage.isIn(DamageTypeTags.IS_EXPLOSION) && crystal) {
            addAttacker(attacker, "crystal explosion");
            return;
        }
        // Require direct player melee damage: arrows, thorns and explosions cannot learn a name.
        if (!learnAttackers.get() || damage.getSource() != attacker) return;
        boolean smash = damage.isOf(DamageTypes.MACE_SMASH);
        if (!smash && !damage.isOf(DamageTypes.PLAYER_ATTACK)) return;
        var held = attacker.getMainHandStack();
        boolean mace = smash || held.isOf(Items.MACE);
        boolean sword = !smash && held.isIn(ItemTags.SWORDS);
        if (!(mace && weapons.get() != Weapons.Sword || sword && weapons.get() != Weapons.Mace)) return;
        addAttacker(attacker, mace ? "mace hit" : "sword hit");
    }
    private void addAttacker(PlayerEntity attacker, String cause) {
        String name = attacker.getGameProfile().getName();
        if (PlayerNameList.matches(names.get(), name)) return;
        var updated = new ArrayList<>(names.get());
        updated.add(name);
        names.set(updated);
        info("Added %s to Name Disconnect after a %s.", name, cause);
    }
    @EventHandler(priority = 1100) private void tick(TickEvent.Post event) {
        refreshCrystals();
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null || names.get().isEmpty()) return;
        for (var player : mc.world.getPlayers()) {
            if (player == mc.player || player.getUuid().equals(mc.player.getUuid()) || !player.isAlive()
                || !PlayerNameList.matches(names.get(), player.getGameProfile().getName())) continue;
            if (detection.get() == Detection.Line_Of_Sight && !mc.player.canSee(player)) continue;
            String reason = "9000Utils: Name Disconnect\nListed player: " + player.getGameProfile().getName()
                + "\nReason: " + (detection.get() == Detection.Line_Of_Sight ? "player entered line of sight" : "player entered loaded render range")
                + String.format(java.util.Locale.ROOT, "\nDistance: %.1f blocks", mc.player.distanceTo(player));
            toggle();
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
            return;
        }
    }
}
