package dev.dragonsnake9000.utils.mixin;

import baritone.utils.pathing.MutableMoveResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Names/descriptors are verified against the exact bundled 1.21.4 Baritone binary. */
@Mixin(value = MutableMoveResult.class, remap = false)
public interface MoveResultAccessor {
    @Accessor("a") int utils9000$getX();
    @Accessor("b") int utils9000$getY();
    @Accessor("c") int utils9000$getZ();
    @Accessor("a") void utils9000$setCost(double value);
}
