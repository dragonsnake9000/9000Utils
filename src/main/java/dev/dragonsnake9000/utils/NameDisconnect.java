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
    public NameDisconnect() { super(Utils9000Addon.CATEGORY, "name-disconnect", "Disconnects when a listed player is loaded nearby or visible."); }

    /** Called on the client thread after vanilla handles a server damage notification. */
    public void observeDamage(EntityDamageS2CPacket packet) {
        if (!isActive() || !learnAttackers.get() || mc.player == null || mc.world == null
            || packet.entityId() != mc.player.getId()) return;
        var damage = packet.createDamageSource(mc.world);
        // Require direct player melee damage: arrows, thorns and explosions cannot learn a name.
        if (!(damage.getAttacker() instanceof PlayerEntity attacker) || damage.getSource() != attacker
            || attacker.getUuid().equals(mc.player.getUuid())) return;
        boolean smash = damage.isOf(DamageTypes.MACE_SMASH);
        if (!smash && !damage.isOf(DamageTypes.PLAYER_ATTACK)) return;
        var held = attacker.getMainHandStack();
        boolean mace = smash || held.isOf(Items.MACE);
        boolean sword = !smash && held.isIn(ItemTags.SWORDS);
        if (!(mace && weapons.get() != Weapons.Sword || sword && weapons.get() != Weapons.Mace)) return;
        String name = attacker.getGameProfile().getName();
        if (PlayerNameList.matches(names.get(), name)) return;
        var updated = new ArrayList<>(names.get());
        updated.add(name);
        names.set(updated);
        info("Added %s to Name Disconnect after a %s hit.", name, mace ? "mace" : "sword");
    }
    @EventHandler(priority = 1100) private void tick(TickEvent.Post event) {
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
