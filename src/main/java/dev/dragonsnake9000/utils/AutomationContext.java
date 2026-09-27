package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.gui.screen.ChatScreen;
import java.util.HashSet;
import java.util.Set;

/** Temporary background-running options; never save over the user's normal preferences. */
public final class AutomationContext {
    private static final Set<Object> owners = new HashSet<>();
    private static boolean previousPause;
    public static boolean active() { return !owners.isEmpty() && MeteorClient.mc.world != null; }
    public static boolean blockedScreen() {
        return MeteorClient.mc.currentScreen != null && !(MeteorClient.mc.currentScreen instanceof ChatScreen);
    }
    static void acquire(Object owner) {
        if (owners.isEmpty()) previousPause = MeteorClient.mc.options.pauseOnLostFocus;
        owners.add(owner);
        MeteorClient.mc.options.pauseOnLostFocus = false;
    }
    static void release(Object owner) {
        if (owners.remove(owner) && owners.isEmpty()) MeteorClient.mc.options.pauseOnLostFocus = previousPause;
    }
}
