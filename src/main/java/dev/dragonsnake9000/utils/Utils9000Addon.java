package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public final class Utils9000Addon extends MeteorAddon {
    public static final Category CATEGORY = new Category("9000Utils");

    @Override public void onInitialize() {
        Modules.get().add(new LavacastPathfinder());
        Modules.get().add(new MossRefill());
        Modules.get().add(new ArmorRefresh());
        Modules.get().add(new MossInventoryRefresh());
        MeteorClient.EVENT_BUS.subscribe(new ActivityPause());
        MeteorClient.EVENT_BUS.subscribe(new MiningGuard());
        MeteorClient.EVENT_BUS.subscribe(new EmptyShulkerDropper());
        MeteorClient.EVENT_BUS.subscribe(new ShulkerHotbarGuard());
    }
    @Override public void onRegisterCategories() { Modules.registerCategory(CATEGORY); }
    @Override public String getPackage() { return "dev.dragonsnake9000.utils"; }
}
