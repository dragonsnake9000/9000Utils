package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
class MossStackPlanTest {
    @Test void combinesManySmallStacksIntoAFullStackWithoutLosingMoss() {
        int[] stacks=new int[36]; for(int i=9;i<14;i++) stacks[i]=15;
        int actions=0;
        for (var plan=MossStackPlan.choose(stacks,64);plan!=null;plan=MossStackPlan.choose(stacks,64)) {
            int moved=Math.min(stacks[plan.source()],64-stacks[plan.destination()]);
            stacks[plan.source()]-=moved; stacks[plan.destination()]+=moved;
            assertEquals(75,Arrays.stream(stacks).sum()); assertTrue(++actions<=4);
        }
        assertEquals(1,Arrays.stream(stacks).filter(n->n==64).count());
        assertEquals(11,Arrays.stream(stacks).filter(n->n<64).sum());
    }
    @Test void ignoresHotbarFullStacksAndInsufficientReserves() {
        int[] stacks=new int[36]; stacks[0]=64;stacks[9]=20;stacks[10]=30;stacks[11]=64;
        assertNull(MossStackPlan.choose(stacks,64));
    }
    @Test void retainsRemainderWhenTwoPartialsExceedStackSize() {
        int[] stacks=new int[36];stacks[9]=50;stacks[10]=20;
        assertEquals(new MossStackPlan.Merge(10,9),MossStackPlan.choose(stacks,64));
    }
    @Test void repeatedConsolidationProducesSeveralFullStacks() {
        int[] stacks=new int[36];Arrays.fill(stacks,9,36,14);int total=27*14;
        for(int limit=0;limit<40;limit++) {
            var plan=MossStackPlan.choose(stacks,64);if(plan==null)break;
            int moved=Math.min(stacks[plan.source()],64-stacks[plan.destination()]);
            stacks[plan.source()]-=moved;stacks[plan.destination()]+=moved;
        }
        assertEquals(total,Arrays.stream(stacks).sum());
        assertEquals(total/64,Arrays.stream(stacks).filter(n->n==64).count());
        assertNull(MossStackPlan.choose(stacks,64));
    }
}
