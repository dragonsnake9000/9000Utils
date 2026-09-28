package dev.dragonsnake9000.utils.mixin;

import baritone.pathing.movement.MovementState;
import baritone.api.utils.Rotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MovementState.MovementTarget.class, remap = false)
public interface MovementTargetAccessor {
    @Accessor("a") Rotation utils9000$getRotation();
}
