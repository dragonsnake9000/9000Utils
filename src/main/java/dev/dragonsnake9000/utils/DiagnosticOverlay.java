package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.render.*;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import static meteordevelopment.meteorclient.MeteorClient.mc;

final class DiagnosticOverlay {
    @EventHandler private void world(Render3DEvent event) {
        if (mc.player==null || mc.world==null) return;
        for (DebugView view:DebugView.ALL) if (view.enabled()) view.render.accept(event);
    }
    @EventHandler private void hud(Render2DEvent event) {
        if (mc.player==null || mc.world==null || mc.options.hudHidden) return;
        TextRenderer text=TextRenderer.get(); text.begin(); double y=12;
        for (DebugView view:DebugView.ALL) if (view.enabled()) {
            String status=view.text.get();
            text.render(view.owner.name+": "+(status==null ? "Active" : status),12,y,Color.WHITE,true);
            y+=text.getHeight()+3;
        }
        text.end();
    }
}
