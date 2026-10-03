package dev.dragonsnake9000.utils;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Optional public-API bridge; no RusherHack classes are linked into the addon. */
final class RusherHackFriends {
    private final String apiName;
    private Method managerGetter, friendCheck;
    private long retryAfter;
    private boolean warned;
    RusherHackFriends() { this("org.rusherhack.client.api.RusherHackAPI"); }
    RusherHackFriends(String apiName) { this.apiName = apiName; }
    boolean isFriend(String username, Consumer<String> warning) {
        if (System.nanoTime() < retryAfter) return false;
        try {
            if (managerGetter == null) {
                Class<?> api = Class.forName(apiName, false, getClass().getClassLoader());
                managerGetter = api.getMethod("getRelationManager");
                // Invoke the public interface method, not an obfuscated implementation class.
                friendCheck = managerGetter.getReturnType().getMethod("isFriend", String.class);
            }
            Object manager = managerGetter.invoke(null);
            if (manager == null) throw new IllegalStateException("Relation manager is not ready");
            // Query live: additions/removals must take effect without restarting Anti Mace.
            return Boolean.TRUE.equals(friendCheck.invoke(manager, username));
        } catch (ClassNotFoundException absent) {
            retryAfter = System.nanoTime() + 5_000_000_000L;
            return false;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            managerGetter = friendCheck = null;
            retryAfter = System.nanoTime() + 5_000_000_000L;
            if (!warned) {
                warning.accept("RusherHack friend API unavailable; Anti Mace cannot exclude its friends until the API recovers.");
                warned = true;
            }
            return false;
        }
    }
}
