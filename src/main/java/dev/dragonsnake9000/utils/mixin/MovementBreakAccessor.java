package dev.dragonsnake9000.utils.mixin;

import baritone.pathing.movement.Movement;
import baritone.api.utils.BetterBlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Required clearance cells, checked against live terrain before mining preparation. */
@Mixin(value = Movement.class, remap = false)
public interface MovementBreakAccessor {
    @Accessor("c") BetterBlockPos utils9000$getPlacement();
    @Accessor("a") BetterBlockPos[] utils9000$getClearance();
    @Accessor("a") baritone.pathing.movement.MovementState utils9000$getState();
}
