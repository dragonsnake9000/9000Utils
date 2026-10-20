package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FluidLayerTest {
    @Test void waterAndLavaAreIndependentlyOptIn() {
        assertTrue(TargetRules.permitsSourceLayer(1,true,false,0,true,true,false));
        assertTrue(TargetRules.permitsSourceLayer(1,false,true,0,true,false,true));
        assertFalse(TargetRules.permitsSourceLayer(1,true,false,0,true,false,true));
        assertFalse(TargetRules.permitsSourceLayer(1,false,true,0,true,true,false));
    }
    @Test void flowingFallingAndStackedFluidsAreNotSingleSourceLayers() {
        for (int level=1;level<=15;level++) assertFalse(TargetRules.permitsSourceLayer(1,true,false,level,true,true,true));
        assertFalse(TargetRules.permitsSourceLayer(1,true,false,0,false,true,true));
        assertFalse(TargetRules.permitsSourceLayer(2,false,true,0,true,true,true));
        assertFalse(TargetRules.permitsSourceLayer(1,false,false,0,true,true,true));
    }
    @Test void sourceExceptionDoesNotIgnoreUpperClearance() {
        assertTrue(TargetRules.hasClearance(64,320,10,y -> y>65 || TargetRules.permitsSourceLayer(y-64,true,false,0,true,true,false)));
        assertFalse(TargetRules.hasClearance(64,320,10,y -> (y>65 && y!=70) || TargetRules.permitsSourceLayer(y-64,true,false,0,true,true,false)));
    }
}
