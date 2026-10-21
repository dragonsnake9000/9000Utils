package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import net.minecraft.item.consume.UseAction;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Reproduce the user's inventory-open/close workaround without clicking or moving any items. */
final class EatingInventoryRecovery {
    private final EatingRecoveryTimer timer = new EatingRecoveryTimer();
    private Screen opened;
    private int pendingOpen;
    private final EatingInventoryHold hold = new EatingInventoryHold();
    private Object world;
    @EventHandler(priority = 1500) private void tick(TickEvent.Pre event) {
        var walker = Modules.get().get(LavacastPathfinder.class);
        if (mc.player == null || mc.world == null || walker == null || !walker.isActive()
            || !walker.eatingInventoryRecovery() || !mc.player.isAlive() || (world != null && world != mc.world)) {
            if (opened != null && mc.currentScreen == opened) mc.setScreen(null);
            opened = null; clearPendingKey(); world = null; timer.reset(); return;
        }
        world = mc.world;
        if (pendingOpen>0) {
            // Vanilla handles the key in its input loop; adopt only an inventory screen it actually opened.
            if(mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof CreativeInventoryScreen) {
                opened=mc.currentScreen;pendingOpen=0;hold.start(walker.eatingOpenTicks());
            } else if(mc.currentScreen!=null || pendingOpen--<=1) {
                pendingOpen=Math.max(1,pendingOpen);clearPendingKey();
                walker.warning("Inventory key did not open the inventory; recovery will retry if eating remains stuck.");
            }
            return;
        }
        if (opened != null) {
            // Never close a different screen opened by the user or server during recovery.
            if (mc.currentScreen != opened) { opened = null; return; }
            if (hold.shouldClose(walker.eatingKeepOpen(), ActivityPause.eatingPauseActive())) {
                mc.setScreen(null); opened = null;
            }
            return;
        }
        if ((mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) || mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        int food = 0;
        for (int slot = 0; slot <= 40; slot++) {
            if (slot >= 36 && slot != 40) continue;
            var stack = mc.player.getInventory().getStack(slot);
            if (stack.getUseAction() == UseAction.EAT || stack.getUseAction() == UseAction.DRINK) food += stack.getCount();
        }
        if (!timer.update(ActivityPause.eatingPauseActive(), food)) return;
        walker.pauseForRefill();
        // Close chat before sending the key, so it can never type E into a chat input.
        if(mc.currentScreen instanceof ChatScreen)mc.setScreen(null);
        if(mc.currentScreen!=null)return;
        pressInventoryKey();
        walker.info("Eating has not consumed food for five seconds. Refreshing inventory (%s/3).", timer.attempts());
    }
    /** Feed a press/release through Minecraft's keyboard handler, rather than constructing a GUI. */
    private void pressInventoryKey() {
        var key=InputUtil.fromTranslationKey(mc.options.inventoryKey.getBoundKeyTranslationKey());
        if(mc.options.inventoryKey.isUnbound())return;
        pendingOpen=10;
        if(key.getCategory()==InputUtil.Type.MOUSE) {
            // A remapped mouse binding still uses the same vanilla inventory action.
            KeyBinding.onKeyPressed(key);
            return;
        }
        int code=key.getCategory()==InputUtil.Type.KEYSYM ? key.getCode() : GLFW.GLFW_KEY_UNKNOWN;
        int scan=key.getCategory()==InputUtil.Type.SCANCODE ? key.getCode() : GLFW.glfwGetKeyScancode(code);
        long window=mc.getWindow().getHandle();
        mc.keyboard.onKey(window,code,scan,GLFW.GLFW_PRESS,0);
        mc.keyboard.onKey(window,code,scan,GLFW.GLFW_RELEASE,0);
    }
    private void clearPendingKey() {
        if(pendingOpen>0)while(mc.options.inventoryKey.wasPressed()) { /* discard our unhandled queued press */ }
        pendingOpen=0;
    }
}
