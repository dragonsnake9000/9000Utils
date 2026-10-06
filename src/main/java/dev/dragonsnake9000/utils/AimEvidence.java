package dev.dragonsnake9000.utils;

/** Angular evidence only: never substitutes for or vetoes the mace dive checks. */
final class AimEvidence {
    static double angle(double lookX, double lookY, double lookZ, double targetX, double targetY, double targetZ) {
        double lengths = Math.sqrt((lookX * lookX + lookY * lookY + lookZ * lookZ)
            * (targetX * targetX + targetY * targetY + targetZ * targetZ));
        if (!Double.isFinite(lengths) || lengths <= 0) return Double.NaN;
        double cosine = (lookX * targetX + lookY * targetY + lookZ * targetZ) / lengths;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosine))));
    }
    static boolean supports(double angle, boolean visible) {
        return visible && Double.isFinite(angle) && angle <= 15;
    }
}
