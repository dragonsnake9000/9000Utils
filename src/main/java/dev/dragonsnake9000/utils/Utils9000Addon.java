package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public final class Utils9000Addon extends MeteorAddon {
    public static final Category CATEGORY = new Category("9000Utils");

    @Override public void onInitialize() {
        BaritoneActivityPause.register();
        Modules.get().add(new LavacastPathfinder());
        Modules.get().add(new MossRefill());
        Modules.get().add(new ArmorRefresh());
        Modules.get().add(new MossInventoryRefresh());
        Modules.get().add(new AntiMace());
        Modules.get().add(new NameDisconnect());
        MeteorClient.EVENT_BUS.subscribe(new RegionOverlay());
        MeteorClient.EVENT_BUS.subscribe(new MossEscape());
        MeteorClient.EVENT_BUS.subscribe(new ActivityPause());
        MeteorClient.EVENT_BUS.subscribe(new EatingInventoryRecovery());
        MeteorClient.EVENT_BUS.subscribe(new GappleInventoryRefresh());
        MeteorClient.EVENT_BUS.subscribe(new ScaffoldPlacementPause());
        MeteorClient.EVENT_BUS.subscribe(new MiningGuard());
        MeteorClient.EVENT_BUS.subscribe(new EmptyShulkerDropper());
        MeteorClient.EVENT_BUS.subscribe(new ShulkerHotbarGuard());
        MeteorClient.EVENT_BUS.subscribe(new ContainerPlacementGuard());
    }
    @Override public void onRegisterCategories() { Modules.registerCategory(CATEGORY); }
    @Override public String getPackage() { return "dev.dragonsnake9000.utils"; }
}
