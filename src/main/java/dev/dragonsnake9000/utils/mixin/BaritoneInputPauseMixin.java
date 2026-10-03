package dev.dragonsnake9000.utils.mixin;

import baritone.api.utils.input.Input;
import baritone.api.event.events.TickEvent;
import baritone.utils.InputOverrideHandler;
import dev.dragonsnake9000.utils.ActivityPause;
import dev.dragonsnake9000.utils.ScaffoldPlacementPause;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stop Baritone's direct click helpers too, not only its path movement updates. */
@Mixin(value = InputOverrideHandler.class, remap = false)
public abstract class BaritoneInputPauseMixin {
    @Inject(method = "setInputForceState", at = @At("HEAD"), cancellable = true)
    private void guardInput(Input input, boolean forced, CallbackInfo ci) {
        if (ActivityPause.isPaused()) {
            ((InputOverrideHandler) (Object) this).clearAllKeys();
            ci.cancel();
        } else if (forced && input == Input.CLICK_RIGHT) ScaffoldPlacementPause.buildingIntent();
    }
    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void pauseHelpers(TickEvent event, CallbackInfo ci) {
        if (!ActivityPause.isPaused()) return;
        ((InputOverrideHandler) (Object) this).clearAllKeys();
        ci.cancel();
    }
}
