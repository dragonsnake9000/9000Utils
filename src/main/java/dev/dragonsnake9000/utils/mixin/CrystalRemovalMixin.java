package dev.dragonsnake9000.utils.mixin;

import dev.dragonsnake9000.utils.NameDisconnect;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Record the actual entity type before the client drops it from the world. */
@Mixin(ClientWorld.class)
public abstract class CrystalRemovalMixin {
    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void rememberCrystal(int id, Entity.RemovalReason reason, CallbackInfo ci) {
        var module = Modules.get().get(NameDisconnect.class);
        if (module != null && (Object) this == meteordevelopment.meteorclient.MeteorClient.mc.world) module.rememberRemovedCrystal(id);
    }
}
