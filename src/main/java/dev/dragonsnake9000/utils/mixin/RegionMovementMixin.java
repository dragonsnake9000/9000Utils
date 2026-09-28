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
    // Catch mining requested by subclasses (bridge/pillar fallbacks) and the in-wall fallback
    // after state calculation but BEFORE the target rotation or attack keys reach the player.
    @Inject(method = "update", at = @At(value = "FIELD",
        target = "Lbaritone/pathing/movement/MovementState;a:Lbaritone/pathing/movement/MovementState$MovementTarget;",
        ordinal = 0), cancellable = true, require = 1)
    private void rejectMiningIntent(CallbackInfoReturnable<MovementStatus> result) {
        var state = ((MovementBreakAccessor) (Object) this).utils9000$getState();
        var access = (MovementStateAccessor) (Object) state;
        if (!Boolean.TRUE.equals(access.utils9000$getInputs().get(baritone.api.utils.input.Input.CLICK_LEFT))) return;
        var rotation = ((MovementTargetAccessor) (Object) access.utils9000$getTarget()).utils9000$getRotation();
        if (!dev.dragonsnake9000.utils.MiningGuard.forbiddenIntent(rotation)) return;
        access.utils9000$getInputs().clear();
        ((baritone.api.pathing.movement.IMovement) (Object) this).reset();
        a.getInputOverrideHandler().clearAllKeys();
        result.setReturnValue(MovementStatus.UNREACHABLE);
    }
    @Shadow @Final public IBaritone a;
    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 1)
    private void constrain(CallbackInfoReturnable<MovementStatus> result) {
        baritone.api.pathing.movement.IMovement movement = (baritone.api.pathing.movement.IMovement) (Object) this;
        BetterBlockPos src = movement.getSrc(), dest = movement.getDest();
        BetterBlockPos feet = a.getPlayerContext().playerFeet();
        if (dev.dragonsnake9000.utils.ScaffoldGuard.abandonMovement()
            || dev.dragonsnake9000.utils.MiningGuard.blocksMovement(a, ((MovementBreakAccessor) (Object) this).utils9000$getClearance())
            || !RegionConstraint.allows(src.x, src.y, src.z) || !RegionConstraint.allows(dest.x, dest.y, dest.z)
            || !RegionConstraint.allows(feet.x, feet.y, feet.z)) {
            a.getInputOverrideHandler().clearAllKeys();
            result.setReturnValue(MovementStatus.UNREACHABLE);
        }
    }
}
