package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

/** Render the selection even when the pathfinder itself is off. */
final class RegionOverlay {
    @EventHandler private void render(Render3DEvent event) {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (walker != null) walker.renderRegion(event);
    }
}
