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
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;

/** Checks loaded world entities, never merely the server-wide tab list. */
public final class NameDisconnect extends Module {
    enum Detection { Render_Distance, Line_Of_Sight }
    enum Weapons { Sword, Mace, Both }
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<List<String>> names = general.add(new StringListSetting.Builder().name("player-names")
        .description("Exact Minecraft account names, case-insensitive. Listed names override friend status.").defaultValue(List.of()).visible(() -> false).build());
    private final Setting<List<String>> reasons = general.add(new StringListSetting.Builder().name("player-reasons")
        .description("Saved username-to-reason pairs, edited with the player rows below.").defaultValue(List.of()).visible(() -> false).build());
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

    @Override public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        fillPlayers(theme, table);
        return table;
    }
    private void fillPlayers(GuiTheme theme, WTable table) {
        table.clear();
        table.add(theme.label("Username")).expandX();
        table.add(theme.label("Reason"));
        table.row();
        for (int i = 0; i < names.get().size(); i++) {
            final int index = i;
            String[] saved = { names.get().get(i) };
            var nameBox = table.add(theme.textBox(saved[0])).minWidth(180).expandX().widget();
            var reasonBox = table.add(theme.dropdown(PlayerReasons.get(reasons.get(), saved[0]))).widget();
            Runnable save = () -> {
                // An external list edit must not make an old GUI row overwrite another name.
                if (index >= names.get().size() || !names.get().get(index).equals(saved[0])) return;
                String name = nameBox.get().trim();
                var updated = new ArrayList<>(names.get());
                updated.set(index, name);
                names.set(updated);
                if (!PlayerNameList.matches(updated, saved[0]))
                    reasons.set(PlayerReasons.set(reasons.get(), saved[0], PlayerReasons.Reason.undefined));
                reasons.set(PlayerReasons.set(reasons.get(), name, reasonBox.get()));
                saved[0] = name;
            };
            nameBox.actionOnUnfocused = save;
            reasonBox.action = save;
            var remove = table.add(theme.minus()).widget();
            remove.action = () -> {
                if (index < names.get().size() && names.get().get(index).equals(saved[0])) {
                    var updated = new ArrayList<>(names.get()); updated.remove(index); names.set(updated);
                    if (!PlayerNameList.matches(updated, saved[0]))
                        reasons.set(PlayerReasons.set(reasons.get(), saved[0], PlayerReasons.Reason.undefined));
                }
                fillPlayers(theme, table);
            };
            table.row();
        }
        var add = table.add(theme.button("Add Player")).widget();
        add.action = () -> { var updated = new ArrayList<>(names.get()); updated.add(""); names.set(updated); fillPlayers(theme, table); };
        var refresh = table.add(theme.button("Refresh List")).widget();
        refresh.action = () -> fillPlayers(theme, table);
    }

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
            addAttacker(attacker, PlayerReasons.Reason.crystal);
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
        addAttacker(attacker, mace ? PlayerReasons.Reason.mace : PlayerReasons.Reason.sword);
    }
    private void addAttacker(PlayerEntity attacker, PlayerReasons.Reason cause) {
        String name = attacker.getGameProfile().getName();
        reasons.set(PlayerReasons.set(reasons.get(), name, cause));
        if (PlayerNameList.matches(names.get(), name)) return;
        var updated = new ArrayList<>(names.get());
        updated.add(name);
        names.set(updated);
        info("Added %s to Name Disconnect. Reason: %s.", name, cause);
    }
    @EventHandler(priority = 1100) private void tick(TickEvent.Post event) {
        refreshCrystals();
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null || names.get().isEmpty()) return;
        for (var player : mc.world.getPlayers()) {
            if (player == mc.player || player.getUuid().equals(mc.player.getUuid()) || !player.isAlive()
                || !PlayerNameList.matches(names.get(), player.getGameProfile().getName())) continue;
            if (detection.get() == Detection.Line_Of_Sight && !mc.player.canSee(player)) continue;
            String reason = "Account: " + mc.player.getGameProfile().getName()
                + "\n9000Utils: Name Disconnect\nListed player: " + player.getGameProfile().getName()
                + "\nReason: " + PlayerReasons.get(reasons.get(), player.getGameProfile().getName()).name()
                + "\nDetection: " + (detection.get() == Detection.Line_Of_Sight ? "player entered line of sight" : "player entered loaded render range")
                + String.format(java.util.Locale.ROOT, "\nLogout coordinates: X %.2f, Y %.2f, Z %.2f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
                + "\nDimension: " + mc.world.getRegistryKey().getValue()
                // Capture local wall-clock time once; the displayed timestamp stays fixed after logout.
                + "\nDate/time: " + java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", java.util.Locale.ROOT))
                + String.format(java.util.Locale.ROOT, "\nDistance: %.1f blocks", mc.player.distanceTo(player));
            toggle();
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
            return;
        }
    }
}
