package dev.dragonsnake9000.utils;

/** Restocks one stack or more of usable lights, then recovers any nonempty supply box. */
public final class LightRefill extends SupplyRefill {
    public LightRefill() { super(true); }
}
