package dev.dragonsnake9000.utils.mixin;

import baritone.pathing.movement.Movement;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import dev.dragonsnake9000.utils.RegionConstraint;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also guard already-computed paths when the user changes bounds while running. */
@Mixin(value = Movement.class, remap = false)
public abstract class RegionMovementMixin {
    @Shadow @Final public IBaritone a;
    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 1)
    private void constrain(CallbackInfoReturnable<MovementStatus> result) {
        baritone.api.pathing.movement.IMovement movement = (baritone.api.pathing.movement.IMovement) (Object) this;
        BetterBlockPos src = movement.getSrc(), dest = movement.getDest();
        BetterBlockPos feet = a.getPlayerContext().playerFeet();
        if (!RegionConstraint.allows(src.x, src.y, src.z) || !RegionConstraint.allows(dest.x, dest.y, dest.z)
            || !RegionConstraint.allows(feet.x, feet.y, feet.z)) {
            a.getInputOverrideHandler().clearAllKeys();
            result.setReturnValue(MovementStatus.UNREACHABLE);
        }
    }
}
