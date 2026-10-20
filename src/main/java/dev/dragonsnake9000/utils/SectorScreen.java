package dev.dragonsnake9000.utils;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Texture-backed sector buttons. State lives in Meteor settings, not in this temporary screen. */
final class SectorScreen extends Screen {
    private static final Identifier MAP=Identifier.of("utils9000","textures/gui/sector_map.png");
    private static final Identifier PIN=Identifier.of("utils9000","textures/gui/you_are_here.png");
    private final SectorWorkArea region;
    private final Screen parent;
    private final Runnable refresh;
    private int left, top, size;
    private long selectionNoticeStarted;
    SectorScreen(SectorWorkArea region, Screen parent, Runnable refresh) {
        super(Text.literal("Sector Work Area")); this.region=region;this.parent=parent;this.refresh=refresh;
    }
    @Override protected void init() {
        size=Math.max(100,Math.min(width-16,height-64));left=(width-size)/2;top=20;
        addDrawableChild(ButtonWidget.builder(Text.literal("Completed selected sector"),b->region.completeSector()).dimensions(width/2-155,height-24,150,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear completion"),b->region.clearCompletion()).dimensions(width/2+5,height-24,150,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->close()).dimensions(4,2,45,18).build());
    }
    private int edgeX(int col) { return left+(int)Math.round(size*(160+col*93.6)/1254.0); }
    private int edgeY(int row) { return top+(int)Math.round(size*(155+row*92.0)/1254.0); }
    // Screen.render invokes renderBackground. Do not blur the map already drawn below.
    @Override public void renderBackground(DrawContext context,int mx,int my,float delta) {}
    @Override public void render(DrawContext context,int mx,int my,float delta) {
        context.fill(0,0,width,height,0xEE101010);
        context.drawTexture(RenderLayer::getGuiTextured,MAP,left,top,0,0,size,size,1254,1254,1254,1254);
        for(int i=0;i<100;i++) {
            int x=edgeX(i%10),y=edgeY(i/10),right=edgeX(i%10+1),bottom=edgeY(i/10+1);
            boolean hover=mx>=x && mx<right && my>=y && my<bottom;
            int color=region.completed(i)?0xBBDDAD18:region.selectedSector()==i?0xAA22C858:hover?0x55FFFFFF:0;
            if(color!=0) context.fill(x+1,y+1,right-1,bottom-1,color);
            float scale=Math.min(1f,(right-x-2f)/textRenderer.getWidth(SectorGrid.name(i)));
            context.getMatrices().push();context.getMatrices().scale(scale,scale,1);
            context.drawCenteredTextWithShadow(textRenderer,SectorGrid.name(i),(int)((x+right)/2/scale),(int)((y+bottom)/2/scale)-4,0xFFFFFFFF);
            context.getMatrices().pop();
        }
        // Live player coordinates, not Freecam coordinates. The pin tip is the exact map anchor.
        if(client.player!=null) {
            double px=client.player.getX(),pz=client.player.getZ();
            if(SectorGrid.at(px,pz)>=0) {
                double mapX=left+size*(160+(px+5000)/10000*936)/1254.0;
                double mapY=top+size*(155+(pz+5000)/10000*920)/1254.0;
                int pinSize=Math.max(32,Math.min(48,size/9));
                // Flush batched font geometry before the marker, then put the pin on a higher GUI layer.
                context.draw();
                context.getMatrices().push();
                context.getMatrices().translate(mapX-pinSize/2.0,mapY-pinSize,200);
                context.drawTexture(RenderLayer::getGuiTextured,PIN,0,0,0,0,pinSize,pinSize,1254,1254,1254,1254);
                context.draw();
                context.getMatrices().pop();
            }
        }
        String chosen=region.selectedSector()<0?"None":SectorGrid.name(region.selectedSector());
        context.drawCenteredTextWithShadow(textRenderer,"Selected: "+chosen+" | Green: selected | Gold: completed",width/2,height-38,0xFFFFFFFF);
        super.render(context,mx,my,delta);
        if(selectionNoticeStarted!=0) {
            double seconds=(System.nanoTime()-selectionNoticeStarted)/1_000_000_000.0;
            int alpha=(int)(255*Math.max(0,Math.min(1,(3-seconds)/2)));
            if(alpha>3) {
                String notice="Lavacast Pathfinder Region Has Been Updated";
                float scale=Math.min(1f,Math.max(1,width-110)/(float)textRenderer.getWidth(notice));
                context.getMatrices().push();context.getMatrices().translate(width/2.0,7,300);context.getMatrices().scale(scale,scale,1);
                context.drawCenteredTextWithShadow(textRenderer,notice,0,0,(alpha<<24)|0xFFFFFF);
                context.getMatrices().pop();
            }
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button==0) for(int i=0;i<100;i++) if(x>=edgeX(i%10)&&x<edgeX(i%10+1)&&y>=edgeY(i/10)&&y<edgeY(i/10+1)) {
            region.selectSector(i);
            selectionNoticeStarted=System.nanoTime();
            client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                net.minecraft.sound.SoundEvents.ENTITY_ARROW_HIT_PLAYER,1f));
            return true;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public void close() { refresh.run();client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
