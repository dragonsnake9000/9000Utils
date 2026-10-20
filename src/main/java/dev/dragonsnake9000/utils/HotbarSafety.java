package dev.dragonsnake9000.utils;
final class HotbarSafety {
    static int choose(boolean[] empty,boolean[] protectedSlots,int preferred) {
        for(int i=0;i<9;i++) if(empty[i] && !protectedSlots[i])return i;
        if(preferred>=0 && preferred<9 && !protectedSlots[preferred])return preferred;
        for(int i=0;i<9;i++)if(!protectedSlots[i])return i;
        return -1;
    }
}
