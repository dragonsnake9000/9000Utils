package dev.dragonsnake9000.utils.mixin;

import baritone.api.utils.BetterBlockPos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import dev.dragonsnake9000.utils.PositionCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ETF compares a rendered block entity position with Vec3i.ZERO. Baritone's unchecked cast crashes. */
@Mixin(value = BetterBlockPos.class, remap = false)
public abstract class BetterBlockPosMixin {
    @Inject(method = "equals(Ljava/lang/Object;)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void compatibleEquals(Object other, CallbackInfoReturnable<Boolean> result) {
        if (other instanceof BlockPos) return; // Preserve Baritone's fast normal path.
        BetterBlockPos self = (BetterBlockPos) (Object) this;
        result.setReturnValue(PositionCompatibility.matches(self.x, self.y, self.z, other));
    }
}
