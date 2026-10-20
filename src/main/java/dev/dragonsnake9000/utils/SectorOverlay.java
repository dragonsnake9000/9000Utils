package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.render.color.Color;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Flat, world-sized bitmap lettering avoids distance-scaled nametag text. */
final class SectorOverlay {
    static void render(Render3DEvent event,int transparency) {
        if(mc.world==null || mc.player==null || transparency>=100) return;
        var camera=mc.gameRenderer.getCamera().getPos();double y=SectorGrid.plane(camera.y);
        // Meteor's overlay meshes render with depth testing and culling disabled: visible through terrain.
        event.renderer.triangles.depthTest=false;
        event.renderer.lines.depthTest=false;
        int alpha=Math.round((100-transparency)*2.55f);
        Color white=new Color(255,255,255,alpha);
        // A faint white plane, with brighter sector boundaries and labels.
        event.renderer.quadHorizontal(-5000,y,-5000,5000,5000,new Color(255,255,255,Math.max(1,alpha/12)));
        for(int i=0;i<=10;i++) {
            double edge=-5000+i*1000;
            event.renderer.quadHorizontal(edge-1,y+.01,-5000,edge+1,5000,white);
            event.renderer.quadHorizontal(-5000,y+.01,edge-1,5000,edge+1,white);
        }
        double range=mc.options.getViewDistance().getValue()*16.0+1000;
        for(int i=0;i<100;i++) {
            double cx=SectorGrid.minX(i)+500,cz=SectorGrid.minZ(i)+500;
            if(Math.abs(camera.x-cx)>range || Math.abs(camera.z-cz)>range) continue;
            SectorLettering.draw(event.renderer,SectorGrid.name(i),cx,cz,white);
        }
    }
}
