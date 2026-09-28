package dev.dragonsnake9000.utils;
import baritone.api.BaritoneAPI;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.registry.Registries;
import java.util.*;

/** Refill interactions lock movement; recovery routes explicitly acquire higher-priority permissions. */
final class PathingSafety {
    private record Permissions(int priority, boolean breaking, boolean placing, boolean moss, Set<Block> surface) {}
    private static final Map<Object, Permissions> owners = new LinkedHashMap<>();
    private static boolean allowBreak, allowPlace;
    private static long scaffoldResume;
    static void suspendScaffolding() { scaffoldResume = System.nanoTime() + 5_000_000_000L; if (!owners.isEmpty()) apply(); }
    private static List<Item> throwaway;
    private static List<Block> disallowed, breakAnyway;
    static boolean allBreakingDisabled() { return !owners.isEmpty() && !current().breaking; }
    static boolean forbidsBreaking(Block block) {
        if (owners.isEmpty()) return false;
        Permissions active = current();
        return !active.breaking || (!active.moss && block == Blocks.MOSS_BLOCK)
            || BaritoneAPI.getSettings().blocksToDisallowBreaking.value.contains(block);
    }
    static void acquire(Object owner) { update(owner, new Permissions(2, false, false, false, Set.of())); }
    static void walking(Object owner, boolean breaking, boolean placing, boolean moss, List<Block> surface) { update(owner, new Permissions(0, breaking, placing, moss, Set.copyOf(surface))); }
    static void recovery(Object owner, boolean moss) {
        var walker = meteordevelopment.meteorclient.systems.modules.Modules.get().get(LavacastPathfinder.class);
        boolean breaking = walker == null || !walker.isActive() || walker.allowBlockBreaking();
        update(owner, new Permissions(2, breaking, true, moss, Set.of()));
    }
    static boolean mayBreakMoss() { return !owners.isEmpty() && current().moss; }
    static boolean protectsSurface(Block block) { return !owners.isEmpty() && current().surface.contains(block); }
    private static Permissions current() { return owners.values().stream().max(Comparator.comparingInt(Permissions::priority)).orElseThrow(); }
    private static void update(Object owner, Permissions permissions) {
        if (permissions.equals(owners.get(owner))) {
            BaritoneAPI.getSettings().allowBreak.value = current().breaking;
            if (!BaritoneAPI.getSettings().allowBreakAnyway.value.isEmpty()) BaritoneAPI.getSettings().allowBreakAnyway.value = new ArrayList<>();
            BaritoneAPI.getSettings().allowPlace.value = current().placing && System.nanoTime() >= scaffoldResume;
            return;
        }
        if (owners.isEmpty()) {
            allowBreak = BaritoneAPI.getSettings().allowBreak.value;
            allowPlace = BaritoneAPI.getSettings().allowPlace.value;
            throwaway = new ArrayList<>(BaritoneAPI.getSettings().acceptableThrowawayItems.value);
            disallowed = new ArrayList<>(BaritoneAPI.getSettings().blocksToDisallowBreaking.value);
            breakAnyway = new ArrayList<>(BaritoneAPI.getSettings().allowBreakAnyway.value);
        }
        owners.put(owner, permissions);
        apply();
    }
    private static void apply() {
        Permissions active = current();
        BaritoneAPI.getSettings().allowBreak.value = active.breaking;
        BaritoneAPI.getSettings().allowBreakAnyway.value = new ArrayList<>();
        BaritoneAPI.getSettings().allowPlace.value = active.placing && System.nanoTime() >= scaffoldResume;
        BaritoneAPI.getSettings().acceptableThrowawayItems.value = new ArrayList<>(List.of(Items.MOSS_BLOCK));
        List<Block> protectedBlocks = new ArrayList<>(disallowed);
        if (active.moss) protectedBlocks.remove(Blocks.MOSS_BLOCK);
        else if (!protectedBlocks.contains(Blocks.MOSS_BLOCK)) protectedBlocks.add(Blocks.MOSS_BLOCK);
        for (Block block : active.surface) if (!protectedBlocks.contains(block)) protectedBlocks.add(block);
        // Never mine the supply box merely to get to a cap position or a dropped item.
        if (active.priority > 0) for (Block block : Registries.BLOCK) {
            if (block instanceof ShulkerBoxBlock && !protectedBlocks.contains(block)) protectedBlocks.add(block);
        }
        BaritoneAPI.getSettings().blocksToDisallowBreaking.value = protectedBlocks;
    }
    static void release(Object owner) {
        if (owners.remove(owner) == null) return;
        if (!owners.isEmpty()) { apply(); return; }
        BaritoneAPI.getSettings().allowBreak.value = allowBreak;
        BaritoneAPI.getSettings().allowPlace.value = allowPlace;
        scaffoldResume = 0;
        BaritoneAPI.getSettings().acceptableThrowawayItems.value = throwaway;
        BaritoneAPI.getSettings().blocksToDisallowBreaking.value = disallowed;
        BaritoneAPI.getSettings().allowBreakAnyway.value = breakAnyway;
    }
}
