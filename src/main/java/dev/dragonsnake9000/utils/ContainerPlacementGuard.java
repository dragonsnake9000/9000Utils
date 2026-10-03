package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Protect direct placement packets too (Sleepy's placer does not always call interactBlock). */
final class ContainerPlacementGuard {
    private boolean forwarding;
    @EventHandler(priority = 500) private void send(PacketEvent.Send event) {
        if (forwarding || ActivityPause.isPaused() || !(event.packet instanceof PlayerInteractBlockC2SPacket packet)
            || mc.player == null || mc.world == null) return;
        var walker = Modules.get().get(LavacastPathfinder.class);
        var refill = Modules.get().get(MossRefill.class);
        if (walker == null || !walker.isActive()) return;
        var pos = packet.getBlockHitResult().getBlockPos();
        if (refill != null && refill.openingSupplyBox(pos)) return;
        if (mc.world.getBlockState(pos).createScreenHandlerFactory(mc.world, pos) == null || mc.player.isSneaking()) return;
        // Preserve the original hit and sequence. Sneak only on the server for this interaction:
        // do not hold the key or move a Freecam camera downward.
        event.cancel();
        forwarding = true;
        try {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
            mc.player.networkHandler.sendPacket(packet);
        } finally {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            forwarding = false;
        }
    }
}
