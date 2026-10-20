package dev.dragonsnake9000.utils;

/** Flat-roof spacing uses Manhattan distance, not a circular Euclidean radius. */
final class LightCoverage {
    static int lightAt(int emission, int dx, int dy, int dz) {
        return Math.max(0, emission - Math.abs(dx) - Math.abs(dy) - Math.abs(dz));
    }
    /** Uniform air attenuation. Opaque or unknown cells are excluded by passable. */
    static <T> java.util.Map<T, Integer> propagate(T source, int emission, int minimum,
            java.util.function.Function<T, Iterable<T>> neighbors, java.util.function.Predicate<T> passable) {
        java.util.Map<T, Integer> light = new java.util.HashMap<>();
        java.util.ArrayDeque<T> queue = new java.util.ArrayDeque<>();
        if (emission < minimum) return light;
        light.put(source, emission); queue.add(source);
        while (!queue.isEmpty()) {
            T position = queue.removeFirst(); int next = light.get(position)-1;
            if (next < minimum) continue;
            for (T adjacent : neighbors.apply(position)) {
                if (light.containsKey(adjacent) || !passable.test(adjacent)) continue;
                light.put(adjacent, next); queue.addLast(adjacent);
            }
        }
        return light;
    }
    static int flatArea(int emission, int minimum) {
        int radius = emission - minimum;
        return radius < 0 ? 0 : 1 + 2 * radius * (radius + 1);
    }
}
