package dev.dragonsnake9000.utils;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import java.util.*;

/** Overlapping combat/refill pauses must not prematurely restart the placer. */
final class ModulePauses {
    private static final Map<Object, Set<String>> holders = new HashMap<>();
    private static final Map<String, Boolean> original = new HashMap<>();
    static void suppressRestore(String name) { if (original.containsKey(name)) original.put(name, false); }
    static void hold(Object owner, List<String> names) {
        holders.put(owner, Set.copyOf(names));
        for (String name : names) {
            Module module = Modules.get().get(name);
            if (module == null) continue;
            original.putIfAbsent(name, module.isActive());
            if (module.isActive()) module.toggle();
        }
    }
    static void release(Object owner) {
        Set<String> names = holders.remove(owner);
        if (names == null) return;
        for (String name : names) {
            if (holders.values().stream().anyMatch(set -> set.contains(name))) continue;
            Boolean restore = original.remove(name);
            Module module = Modules.get().get(name);
            if (Boolean.TRUE.equals(restore) && module != null && !module.isActive()) module.toggle();
        }
    }
}
