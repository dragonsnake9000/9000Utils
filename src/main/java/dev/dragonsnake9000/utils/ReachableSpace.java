package dev.dragonsnake9000.utils;

import java.util.*;

/** Bounded flood fill of standing positions, not distance repeatedly walked inside a pocket. */
final class ReachableSpace {
    record Pos(int x, int y, int z) {
        Pos add(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    }
    enum Cell { Open, Solid, Unknown }
    interface Terrain { Cell cell(Pos pos); }
    static int count(Pos start, int limit, Terrain terrain) {
        Search search = new Search(terrain);
        // A known solid overlapping the body is zero usable space, not unknown terrain.
        Cell feet = terrain.cell(start), head = terrain.cell(start.add(0, 1, 0));
        if (feet == Cell.Unknown || head == Cell.Unknown) return -1;
        if (feet == Cell.Solid || head == Cell.Solid) return 0;
        if (!search.stand(start)) return -1;
        Set<Pos> seen = new HashSet<>();
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        seen.add(start); queue.add(start);
        int[][] directions = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        while (!queue.isEmpty()) {
            Pos p = queue.remove();
            for (int[] d : directions) {
                for (int dy = -3; dy <= 1; dy++) {
                    Pos next = p.add(d[0], dy, d[1]);
                    if (!search.stand(next)) continue;
                    boolean clear = true;
                    // Clearance through a one-block rise or safe short drop.
                    for (int y = Math.min(0, dy); y <= Math.max(0, dy) + 1; y++)
                        if (!search.open(p.add(d[0], y, d[1]))) { clear = false; break; }
                    if (dy > 0) clear = search.open(p.add(0, 2, 0)) && search.open(next.add(0, 1, 0));
                    if (clear && seen.add(next)) queue.add(next);
                }
                // Count straight sprint-jump exits too; conservatively accept generous clearance.
                for (int distance = 2; distance <= 4; distance++) {
                    Pos next = p.add(d[0] * distance, 0, d[1] * distance);
                    if (!search.stand(next)) continue;
                    boolean clear = search.open(p.add(0, 2, 0));
                    for (int step = 1; step <= distance && clear; step++)
                        for (int y = 1; y <= 2; y++)
                            if (!search.open(p.add(d[0] * step, y, d[1] * step))) { clear = false; break; }
                    if (clear && seen.add(next)) queue.add(next);
                }
            }
            if (seen.size() > limit) return seen.size();
            if (search.unknown) return -1;
        }
        return search.unknown ? -1 : seen.size();
    }
    static boolean opensExit(int before, int after, int limit) {
        // An entirely enclosed player can first escape into a smaller safe pocket.
        return after > limit || (before >= 0 && before <= 1 && after > Math.max(1, before));
    }
    private static final class Search {
        final Terrain terrain;
        boolean unknown;
        Search(Terrain terrain) { this.terrain = terrain; }
        Cell cell(Pos p) {
            Cell value = terrain.cell(p);
            if (value == Cell.Unknown) unknown = true;
            return value;
        }
        boolean open(Pos p) { return cell(p) == Cell.Open; }
        boolean stand(Pos p) {
            return open(p) && open(p.add(0, 1, 0)) && cell(p.add(0, -1, 0)) == Cell.Solid;
        }
    }
}
