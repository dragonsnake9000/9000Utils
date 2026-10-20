package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

/** Optional cleanup shares Roof Lighting's placement/refill locks instead of competing for the hotbar. */
public final class RoofGapFiller extends Module {
    private final Setting<Integer> minimum = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("minimum-newly-lit-blocks").description("Minimum dark cells improved by a cleanup light. 1 fills tiny shadows but can spend many lights; enable cleanup only after the grid pass.")
        .defaultValue(1).range(1,1000).sliderRange(1,421).build());
    private boolean ownedRoof;
    private boolean stopping;
    public RoofGapFiller() { super(Utils9000Addon.CATEGORY,"roof-light-gap-filler",
        "Optional opportunistic cleanup after the fixed grid. Uses Roof Lighting settings and supply handling; disabled by default."); }
    int minimumNewlyLit() { return minimum.get(); }
    @Override public void onActivate() {
        var roof=Modules.get().get(RoofLighting.class);
        ownedRoof=roof!=null && !roof.isActive();
        if (ownedRoof) roof.toggle();
    }
    @Override public void onDeactivate() {
        stopping=true;
        boolean releaseRoof=ownedRoof;
        ownedRoof=false;
        var roof=Modules.get().get(RoofLighting.class);
        if (releaseRoof && roof!=null && roof.isActive()) roof.toggle();
        stopping=false;
    }
    // Meteor calls onDeactivate before clearing isActive; avoid mutually recursive toggles.
    void stopWithRoof() {
        ownedRoof=false;
        if (isActive() && !stopping) toggle();
    }
}
