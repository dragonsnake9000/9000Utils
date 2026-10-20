package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import java.util.*;

/** Independent map UI; choosing a sector applies its bounds to Lavacast Pathfinder. */
public final class SectorWorkArea extends Module {
    private final Setting<Boolean> grid=settings.getDefaultGroup().add(new BoolSetting.Builder().name("show-sector-grid").defaultValue(true).build());
    private final Setting<Integer> transparency=settings.getDefaultGroup().add(new IntSetting.Builder().name("sector-grid-transparency").defaultValue(70).range(0,100).sliderRange(0,100).build());
    private final Setting<Integer> selected=settings.getDefaultGroup().add(new IntSetting.Builder().name("selected-sector").defaultValue(-1).range(-1,99).visible(()->false).build());
    // Keep the serialized key so existing gold sectors survive the terminology change.
    private final Setting<List<String>> verified=settings.getDefaultGroup().add(new StringListSetting.Builder().name("verified-sectors").defaultValue(List.of()).visible(()->false).build());
    public SectorWorkArea() { super(Utils9000Addon.CATEGORY,"sector-work-area","Project sector map, saved completion and overhead grid. Select sectors to set Lavacast Pathfinder's region."); }
    @Override public WWidget getWidget(GuiTheme theme) {
        var root=theme.verticalList();root.add(theme.label("Sector Work Area - click to choose a sector"));
        root.add(new SectorMapButton(()->mc.setScreen(new SectorScreen(this,mc.currentScreen,()->{}))));return root;
    }
    int selectedSector() { return selected.get(); }
    boolean completed(int index) { return verified.get().contains(SectorGrid.name(index)); }
    void selectSector(int index) {
        if(index<0 || index>=100)return;
        var walker=Modules.get().get(LavacastPathfinder.class);
        if(walker==null)return;
        selected.set(index);walker.selectSector(index);
    }
    void completeSector() {
        if(selected.get()<0)return;
        var next=new ArrayList<>(verified.get());String name=SectorGrid.name(selected.get());
        if(!next.contains(name))next.add(name);verified.set(next);
    }
    void clearCompletion() {
        if(selected.get()<0)return;
        var next=new ArrayList<>(verified.get());next.remove(SectorGrid.name(selected.get()));verified.set(next);
    }
    void renderGrid(meteordevelopment.meteorclient.events.render.Render3DEvent event) {
        if(grid.get())SectorOverlay.render(event,transparency.get());
    }
    String sectorStatus() {
        if(!grid.get() || mc.player==null)return null;
        int index=SectorGrid.at(mc.player.getX(),mc.player.getZ());
        return index<0?"Sector: outside project":"Sector: "+SectorGrid.name(index);
    }
}
