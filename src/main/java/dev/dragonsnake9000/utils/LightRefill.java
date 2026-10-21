package dev.dragonsnake9000.utils;

/** Restocks up to one stack of usable lights, then recovers any nonempty supply box. */
public final class LightRefill extends SupplyRefill {
    public LightRefill() { super(true); }
}
