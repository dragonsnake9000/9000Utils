package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

/** Render the selection even when the pathfinder itself is off. */
final class RegionOverlay {
    @EventHandler private void hud(meteordevelopment.meteorclient.events.render.Render2DEvent event) {
        var mc=meteordevelopment.meteorclient.MeteorClient.mc;
        var walker=Modules.get().get(SectorWorkArea.class);
        if(mc.player==null || mc.options.hudHidden || walker==null)return;
        String label=walker.sectorStatus();if(label==null)return;
        var text=meteordevelopment.meteorclient.renderer.text.TextRenderer.get();text.begin();
        text.render(label,12,mc.getWindow().getHeight()-40,meteordevelopment.meteorclient.utils.render.color.Color.WHITE,true);text.end();
    }
    @EventHandler private void render(Render3DEvent event) {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (walker != null) walker.renderRegion(event);
        var sectors=Modules.get().get(SectorWorkArea.class);
        if(sectors!=null)sectors.renderGrid(event);
    }
}
