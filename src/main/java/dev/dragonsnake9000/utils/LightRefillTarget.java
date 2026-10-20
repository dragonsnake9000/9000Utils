package dev.dragonsnake9000.utils;

final class LightRefillTarget {
    static final int BLOCKS=128;
    static boolean wouldOverfill(int held,int incoming) { return held+incoming>BLOCKS; }
    static boolean mayFillPartial(int held,int stackCount,int stackMax) {
        return stackCount>0 && stackCount<stackMax && stackMax-stackCount<=BLOCKS-held;
    }
}
