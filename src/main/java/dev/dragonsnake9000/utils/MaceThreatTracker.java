package dev.dragonsnake9000.utils;

import java.util.ArrayDeque;

/** Uses observed remote positions, not a single velocity packet or equipment change. */
final class MaceThreatTracker {
    record Sample(int tick, double x, double y, double z) {}
    record Rules(double speed, double drop, double radius, double horizon, boolean flightEvidence, int confirmations) {}
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private int lastFlight = -1000, lastSwap = -1000, confirmations;
    private boolean wasElytra;
    private String reasons = "";
    String reasons() { return reasons; }

    boolean update(Sample now, boolean grounded, boolean elytra, boolean chestplate, boolean gliding, boolean mace,
                   double ownX, double ownY, double ownZ, double ownVx, double ownVy, double ownVz, Rules rules) {
        Sample previous = samples.peekLast();
        if (previous != null && (now.tick <= previous.tick || now.tick - previous.tick > 2
            || distanceSquared(now, previous) > 256)) reset(); // Discontinuities are not proof of a dive.
        previous = samples.peekLast();
        samples.addLast(now);
        while (samples.size() > 1 && now.tick - samples.peekFirst().tick > 8) samples.removeFirst();
        Sample first = samples.peekFirst();
        int ticks = now.tick - first.tick;
        double drop = first.y - now.y;
        double down = ticks == 0 ? 0 : drop / ticks;
        boolean rapid = !grounded && ticks >= 3 && down >= rules.speed && drop >= rules.drop
            && previous != null && now.y < previous.y;
        if (grounded) { lastFlight = lastSwap = -1000; samples.clear(); samples.add(now); }
        else if (gliding) lastFlight = now.tick;
        // A chestplate swap is evidence ONLY during an already-observed rapid descent.
        if (rapid && wasElytra && !elytra && chestplate) lastSwap = now.tick;
        wasElytra = elytra;
        boolean flight = now.tick - lastFlight <= 40 || now.tick - lastSwap <= 20;
        double height = now.y - ownY;
        double relativeDown = down + ownVy;
        double impact = relativeDown > 0 ? Math.max(0, height - 1.5) / relativeDown : Double.POSITIVE_INFINITY;
        double dx = now.x - ownX, dz = now.z - ownZ;
        double vx = ticks == 0 ? 0 : (now.x - first.x) / ticks - ownVx;
        double vz = ticks == 0 ? 0 : (now.z - first.z) / ticks - ownVz;
        double px = dx + vx * impact, pz = dz + vz * impact;
        boolean approaching = dx * vx + dz * vz < -.02 || dx * dx + dz * dz <= 2.25;
        boolean threat = mace && rapid && height >= 2 && height <= 32 && impact <= rules.horizon
            && approaching && px * px + pz * pz <= rules.radius * rules.radius
            && (!rules.flightEvidence || flight);
        confirmations = threat ? confirmations + 1 : 0;
        reasons = threat ? String.format(java.util.Locale.ROOT,
            "\n- Mace held in main hand\n- Airborne, %.1f blocks above you\n- Descending %.2f blocks/tick; %.1f Y lost in %d ticks\n- Approaching you; predicted pass %.2f blocks away in %.1f ticks\n- Confirmed for %d consecutive ticks",
            height, down, drop, ticks, Math.hypot(px, pz), impact, confirmations)
            + (now.tick - lastFlight <= 40 ? "\n- Recent elytra flight observed" : "")
            + (now.tick - lastSwap <= 20 ? "\n- Elytra-to-chestplate swap during rapid airborne descent" : "") : "";
        return confirmations >= rules.confirmations;
    }
    private static double distanceSquared(Sample a, Sample b) {
        return Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2) + Math.pow(a.z - b.z, 2);
    }
    void reset() { samples.clear(); confirmations = 0; lastFlight = lastSwap = -1000; wasElytra = false; reasons = ""; }
}
