package dev.dragonsnake9000.utils.mixin;

import baritone.pathing.calc.AStarPathFinder;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Moves;
import baritone.api.pathing.movement.ActionCosts;
import baritone.utils.pathing.MutableMoveResult;
import dev.dragonsnake9000.utils.RegionConstraint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reject out-of-region edges during A*, including dynamic fall and parkour destinations. */
@Mixin(value = AStarPathFinder.class, remap = false)
public abstract class RegionSearchMixin {
    // Baritone's published binary obfuscates calculate0/apply to 'a'; descriptors disambiguate them.
    @Redirect(method = "a(JJ)Ljava/util/Optional;", at = @At(value = "INVOKE",
        target = "Lbaritone/pathing/movement/Moves;a(Lbaritone/pathing/movement/CalculationContext;IIILbaritone/utils/pathing/MutableMoveResult;)V"), require = 1)
    private void constrain(Moves move, CalculationContext context, int x, int y, int z, MutableMoveResult result) {
        move.a(context, x, y, z, result);
        MoveResultAccessor access = (MoveResultAccessor) (Object) result;
        if (dev.dragonsnake9000.utils.ScaffoldGuard.rejected(access.utils9000$getX(), access.utils9000$getY(), access.utils9000$getZ())
            || !dev.dragonsnake9000.utils.ContourRoute.allows(x, y, z, access.utils9000$getX(), access.utils9000$getY(), access.utils9000$getZ())
            || !RegionConstraint.allows(x, y, z) || !RegionConstraint.allows(access.utils9000$getX(), access.utils9000$getY(), access.utils9000$getZ())) {
            access.utils9000$setCost(ActionCosts.COST_INF);
        }
    }
}
