package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.renderer.Texture;
import javax.imageio.ImageIO;
import java.io.IOException;

/** Clickable miniature of the actual map, uploaded once and shared by settings screens. */
final class SectorMapButton extends WWidget {
    private static Texture texture;
    private final Runnable action;
    SectorMapButton(Runnable action) { this.action=action;tooltip="Sector Work Area - click to choose a sector"; }
    @Override protected void onCalculateSize() { width=theme.scale(180);height=theme.scale(180); }
    @Override protected void onRender(GuiRenderer renderer,double mx,double my,double delta) {
        if(texture==null) {
            try(var in=SectorMapButton.class.getResourceAsStream("/assets/utils9000/textures/gui/sector_map.png")) {
                var image=ImageIO.read(in);byte[] rgba=new byte[image.getWidth()*image.getHeight()*4];int n=0;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                    int p=image.getRGB(x,y);rgba[n++]=(byte)(p>>16);rgba[n++]=(byte)(p>>8);rgba[n++]=(byte)p;rgba[n++]=(byte)(p>>24);
                }
                texture=new Texture(image.getWidth(),image.getHeight(),rgba,Texture.Format.RGBA,Texture.Filter.LinearMipmapLinear,Texture.Filter.Linear);
            } catch(IOException e) { throw new IllegalStateException("Cannot load sector map",e); }
        }
        renderer.texture(x,y,width,height,0,texture);
    }
    @Override public boolean onMouseClicked(double mx,double my,int button,boolean used) {
        if(!used && button==0 && mouseOver) { action.run();return true; }return false;
    }
}
