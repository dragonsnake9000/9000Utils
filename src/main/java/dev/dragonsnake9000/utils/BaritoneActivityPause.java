package dev.dragonsnake9000.utils;

import baritone.api.BaritoneAPI;
import baritone.api.process.*;

/** Keep every normal Baritone process from issuing work during an activity pause. */
final class BaritoneActivityPause implements IBaritoneProcess {
    static void register() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().registerProcess(new BaritoneActivityPause());
    }
    @Override public boolean isActive() { return ActivityPause.isPaused(); }
    @Override public double priority() { return Double.MAX_VALUE; }
    @Override public boolean isTemporary() { return true; }
    @Override public void onLostControl() {}
    @Override public String displayName0() { return "9000Utils activity pause"; }
    @Override public PathingCommand onTick(boolean failed, boolean safe) {
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }
}
