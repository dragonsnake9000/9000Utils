package dev.dragonsnake9000.utils.mixin;

import baritone.pathing.movement.MovementState;
import baritone.api.utils.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(value = MovementState.class, remap = false)
public interface MovementStateAccessor {
    @Accessor("a") Map<Input, Boolean> utils9000$getInputs();
    @Accessor("a") MovementState.MovementTarget utils9000$getTarget();
}
