package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Guard both vanilla interactions and Sleepy's direct/airplace packets, including silent offhand swaps. */
public final class LightPlacementGuard {
    private static boolean swapped;
    private static ItemStack swappedMain=ItemStack.EMPTY, swappedOff=ItemStack.EMPTY;
    static BlockPos lastBlocked;
    static int lastBlockedTick;
    @EventHandler(priority=1900) private void tick(TickEvent.Pre event) { swapped=false; }
    @EventHandler(priority=1600) private void send(PacketEvent.Send event) {
        if (mc.player==null || mc.world==null) return;
        if (event.packet instanceof PlayerActionC2SPacket packet && packet.getAction()==PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            if (!swapped) { swappedMain=mc.player.getOffHandStack().copy(); swappedOff=mc.player.getMainHandStack().copy(); }
            swapped=!swapped;
        }
        if (event.packet instanceof PlayerInteractBlockC2SPacket packet && block(packet.getHand(),packet.getBlockHitResult())) event.cancel();
    }
    public static boolean block(Hand hand, BlockHitResult hit) {
        if (mc.player==null || mc.world==null) return false;
        var walker=Modules.get().get(LavacastPathfinder.class);
        var roof=Modules.get().get(RoofLighting.class);
        boolean enabled=(walker!=null && walker.isActive() && walker.protectLightSources())
            || (roof!=null && roof.isActive() && roof.protectLightSources());
        ItemStack stack=swapped ? (hand==Hand.MAIN_HAND ? swappedMain : swappedOff) : mc.player.getStackInHand(hand);
        if (!enabled || !stack.isOf(Items.MOSS_BLOCK)) return false;
        BlockPos destination=mc.world.getBlockState(hit.getBlockPos()).isReplaceable() ? hit.getBlockPos() : hit.getBlockPos().offset(hit.getSide());
        if (nearEmitter(destination)) {
            lastBlocked=destination.toImmutable(); lastBlockedTick=mc.player.age; return true;
        }
        return false;
    }
    static boolean nearEmitter(BlockPos destination) {
        if (mc.world==null) return false;
        for (Direction side:Direction.values()) if (mc.world.getBlockState(destination.offset(side)).getLuminance()>0) return true;
        return false;
    }
}
