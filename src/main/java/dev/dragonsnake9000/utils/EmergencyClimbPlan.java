package dev.dragonsnake9000.utils;
import java.util.*;
import java.util.function.Function;
/** Small supported walking routes, allowing explicitly selected obstructions to be cleared. */
final class EmergencyClimbPlan {
    record Pos(int x,int y,int z) { Pos add(int x,int y,int z){return new Pos(this.x+x,this.y+y,this.z+z);} }
    enum Cell { AIR, FLOOR, BREAKABLE, BLOCKED }
    record Step(Pos feet,List<Pos> clear) {}
    private record Node(Pos feet,List<Step> steps,Set<Pos> removed,Set<Pos> floors) {}
    static List<Step> find(Pos origin,Function<Pos,Cell> cells) {
        ArrayDeque<Node> queue=new ArrayDeque<>();
        queue.add(new Node(origin,List.of(),Set.of(),Set.of(origin.add(0,-1,0))));
        Set<Pos> visited=new HashSet<>();visited.add(origin);
        Node best=null;
        while(!queue.isEmpty()) {
            Node n=queue.removeFirst();
            for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy:new int[]{1,0,-1}) {
                Pos p=n.feet.add(direction[0],dy,direction[1]);
                int x=p.x-origin.x,y=p.y-origin.y,z=p.z-origin.z;
                if(x*x+y*y+z*z>9 || visited.contains(p))continue;
                Pos floor=p.add(0,-1,0);Cell below=cells.apply(floor);
                if(n.removed.contains(floor) || (below!=Cell.FLOOR && below!=Cell.BREAKABLE))continue;
                LinkedHashSet<Pos> clear=new LinkedHashSet<>(List.of(n.feet,n.feet.add(0,1,0),p,p.add(0,1,0)));
                if(dy>0)clear.add(n.feet.add(0,2,0));
                if(dy<0)clear.add(p.add(0,2,0));
                List<Pos> breaks=new ArrayList<>();boolean valid=true;
                for(Pos c:clear) {
                    if(n.removed.contains(c))continue;
                    Cell kind=cells.apply(c);
                    if(kind==Cell.AIR)continue;
                    if(kind!=Cell.BREAKABLE || n.floors.contains(c) || c.equals(floor)){valid=false;break;}
                    breaks.add(c);
                }
                if(!valid || n.removed.size()+breaks.size()>8)continue;
                Set<Pos> removed=new HashSet<>(n.removed);removed.addAll(breaks);
                Set<Pos> floors=new HashSet<>(n.floors);floors.add(floor);
                List<Step> steps=new ArrayList<>(n.steps);steps.add(new Step(p,List.copyOf(breaks)));
                Node next=new Node(p,List.copyOf(steps),removed,floors);visited.add(p);queue.addLast(next);
                if(p.y>=origin.y && (best==null || p.y>best.feet.y || (p.y==best.feet.y
                    && (removed.size()<best.removed.size() || (removed.size()==best.removed.size() && steps.size()<best.steps.size())))))best=next;
            }
        }
        return best==null ? List.of() : best.steps;
    }
}
