package dev.dragonsnake9000.utils.mixin;

import dev.dragonsnake9000.utils.MiningGuard;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gate actual attacks, not just path costs, and pause the placer before sending a break. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class MiningInteractionMixin {
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true, require = 1)
    private void beforePlace(net.minecraft.client.network.ClientPlayerEntity player, net.minecraft.util.Hand hand,
                             net.minecraft.util.hit.BlockHitResult hit,
                             CallbackInfoReturnable<net.minecraft.util.ActionResult> result) {
        if (dev.dragonsnake9000.utils.ShulkerHotbarGuard.blockPlacement(hand)
            || dev.dragonsnake9000.utils.ScaffoldGuard.beforePlace(hand, hit))
            result.setReturnValue(net.minecraft.util.ActionResult.FAIL);
    }

    @Inject(method = {"attackBlock", "updateBlockBreakingProgress"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void beforeBreak(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> result) {
        if (MiningGuard.beforeBreak(pos)) result.setReturnValue(false);
    }
}
