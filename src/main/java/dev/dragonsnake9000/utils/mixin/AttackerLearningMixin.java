package dev.dragonsnake9000.utils.mixin;

import dev.dragonsnake9000.utils.NameDisconnect;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TAIL runs after vanilla's main-thread dispatch, preserving equipment/packet ordering. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class AttackerLearningMixin {
    @Inject(method = "onEntityDamage", at = @At("TAIL"))
    private void learnAttacker(EntityDamageS2CPacket packet, CallbackInfo ci) {
        var module = Modules.get().get(NameDisconnect.class);
        if (module != null) module.observeDamage(packet);
    }
}
