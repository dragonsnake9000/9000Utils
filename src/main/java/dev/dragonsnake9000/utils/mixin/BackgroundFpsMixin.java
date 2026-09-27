package dev.dragonsnake9000.utils.mixin;

import dev.dragonsnake9000.utils.AutomationContext;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Avoid vanilla's minimized/AFK five-FPS throttle while automation needs game ticks. */
@Mixin(InactivityFpsLimiter.class)
public abstract class BackgroundFpsMixin {
    @Inject(method = "update", at = @At("RETURN"), cancellable = true, require = 1)
    private void keepTicking(CallbackInfoReturnable<Integer> result) {
        if (AutomationContext.active() && result.getReturnValueI() < 30) result.setReturnValue(30);
    }
}
