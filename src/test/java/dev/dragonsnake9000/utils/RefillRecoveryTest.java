package dev.dragonsnake9000.utils;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RefillRecoveryTest {
    @Test void allowsExactlyTenRetriesAndResetsOnlyExplicitly() {
        var budget=new RefillRetryBudget();
        for(int i=1;i<=10;i++){assertTrue(budget.next());assertEquals(i,budget.attempts());}
        assertFalse(budget.next());assertFalse(budget.next());assertEquals(10,budget.attempts());
        budget.reset();assertTrue(budget.next());assertEquals(1,budget.attempts());
    }
    @Test void retainsBothSlotsEvenForAnEmptyingBox() {
        assertFalse(RefillPlanner.canTakeMossStack(64,2,0));
        assertTrue(RefillPlanner.canTakeMossStack(64,3,0));
        assertTrue(RefillPlanner.canTakeMossStack(16,2,16));
        assertFalse(RefillPlanner.canTakeMossStack(17,2,16));
    }
    @Test void quickMovePreservesTwoSlotsAndOneMainSlot() {
        assertFalse(RefillPlanner.canQuickMoveMoss(64,5,1,0));
        assertFalse(RefillPlanner.canQuickMoveMoss(64,2,2,0));
        assertTrue(RefillPlanner.canQuickMoveMoss(64,3,2,0));
        assertTrue(RefillPlanner.canQuickMoveMoss(16,2,1,16));
        assertFalse(RefillPlanner.canQuickMoveMoss(17,2,1,16));
        assertFalse(RefillPlanner.canQuickMoveMoss(16,4,0,16));
    }
    @Test void picksHigherSupportedGroundAndStaysWithinThreeBlocks() {
        var origin=new EmergencyClimbPlan.Pos(0,1,0);
        var path=EmergencyClimbPlan.find(origin,p->{
            if(p.y()<=0)return EmergencyClimbPlan.Cell.FLOOR;
            if(p.x()==1 && p.z()==0 && p.y()==1)return EmergencyClimbPlan.Cell.BREAKABLE;
            if(p.x()==2 && p.z()==0 && p.y()<=2)return EmergencyClimbPlan.Cell.BREAKABLE;
            return EmergencyClimbPlan.Cell.AIR;
        });
        assertFalse(path.isEmpty());assertEquals(3,path.getLast().feet().y());
        for(var step:path) {
            var p=step.feet();assertTrue(p.x()*p.x()+(p.y()-1)*(p.y()-1)+p.z()*p.z()<=9);
            assertFalse(step.clear().contains(new EmergencyClimbPlan.Pos(p.x(),p.y()-1,p.z())));
        }
    }
    @Test void cannotCrossUnknownFluidOrUnbreakableBody() {
        var origin=new EmergencyClimbPlan.Pos(0,1,0);
        assertTrue(EmergencyClimbPlan.find(origin,p->p.y()==0?EmergencyClimbPlan.Cell.FLOOR
            :p.x()==0&&p.z()==0?EmergencyClimbPlan.Cell.AIR:EmergencyClimbPlan.Cell.BLOCKED).isEmpty());
    }
    @Test void clearsAnOverheadBlockForASupportedStepOutOfHole() {
        var origin=new EmergencyClimbPlan.Pos(0,1,0);
        var path=EmergencyClimbPlan.find(origin,p->{
            if(p.y()==0 || (p.x()==1&&p.y()==1&&p.z()==0))return EmergencyClimbPlan.Cell.FLOOR;
            if(p.x()==0&&p.z()==0&&p.y()==3)return EmergencyClimbPlan.Cell.BREAKABLE;
            if(p.x()==0&&p.z()==0&&p.y()>=1 || p.x()==1&&p.z()==0&&p.y()>=2)return EmergencyClimbPlan.Cell.AIR;
            return EmergencyClimbPlan.Cell.BLOCKED;
        });
        assertFalse(path.isEmpty());assertEquals(2,path.getLast().feet().y());
        assertTrue(path.getFirst().clear().contains(new EmergencyClimbPlan.Pos(0,3,0)));
    }
}
