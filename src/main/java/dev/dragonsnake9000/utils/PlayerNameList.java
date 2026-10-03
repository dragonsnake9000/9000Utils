package dev.dragonsnake9000.utils;

import java.util.List;

final class PlayerNameList {
    static boolean matches(List<String> names, String account) {
        return names.stream().anyMatch(name -> name.trim().equalsIgnoreCase(account));
    }
}
