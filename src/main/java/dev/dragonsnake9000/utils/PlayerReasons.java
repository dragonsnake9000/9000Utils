package dev.dragonsnake9000.utils;

import java.util.*;

/** Persist reasons by normalized account name so list edits never shift another player's reason. */
final class PlayerReasons {
    enum Reason { undefined, sword, mace, crystal }
    static String key(String name) { return name.trim().toLowerCase(Locale.ROOT); }
    static Reason get(List<String> entries, String name) {
        for (String entry : entries) {
            int separator = entry.indexOf('=');
            if (separator < 0 || !key(entry.substring(0, separator)).equals(key(name))) continue;
            try { return Reason.valueOf(entry.substring(separator + 1)); }
            catch (IllegalArgumentException ignored) { return Reason.undefined; }
        }
        return Reason.undefined;
    }
    static List<String> set(List<String> entries, String name, Reason reason) {
        var result = new ArrayList<>(entries);
        String account = key(name);
        result.removeIf(entry -> {
            int separator = entry.indexOf('=');
            return separator >= 0 && key(entry.substring(0, separator)).equals(account);
        });
        if (!account.isEmpty() && reason != Reason.undefined) result.add(account + "=" + reason.name());
        return result;
    }
}
