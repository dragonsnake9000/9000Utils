package dev.dragonsnake9000.utils;

import java.util.*;
import java.util.function.*;

/** Incremental Dijkstra search: compare real walking cost against the saved return trail. */
final class BoundedRouteSearch<T> {
    private record Entry<T>(T node,double cost) {}
    private final PriorityQueue<Entry<T>> open=new PriorityQueue<>(Comparator.comparingDouble(Entry::cost));
    private final Map<T,Double> costs=new HashMap<>();
    private final Map<T,T> parents=new HashMap<>();
    private final Function<T,List<T>> neighbors;
    private final ToDoubleBiFunction<T,T> distance;
    private final Predicate<T> destination;
    private final double maximumCost;
    private final int maximumNodes;
    private boolean done;
    private List<T> route=List.of();
    BoundedRouteSearch(T start,Function<T,List<T>> neighbors,ToDoubleBiFunction<T,T> distance,
            Predicate<T> destination,double maximumCost,int maximumNodes) {
        this.neighbors=neighbors;this.distance=distance;this.destination=destination;
        this.maximumCost=maximumCost;this.maximumNodes=maximumNodes;
        costs.put(start,0.0);open.add(new Entry<>(start,0));
    }
    boolean step(int budget) {
        while(!done && budget-->0) {
            if(open.isEmpty()) { done=true;break; }
            Entry<T> entry=open.remove();T node=entry.node();
            if(entry.cost()!=costs.get(node))continue;
            if(entry.cost()>maximumCost) { done=true;break; }
            if(destination.test(node)) {
                LinkedList<T> path=new LinkedList<>();
                for(T at=node;at!=null;at=parents.get(at))path.addFirst(at);
                route=List.copyOf(path);done=true;break;
            }
            for(T next:neighbors.apply(node)) {
                double cost=entry.cost()+distance.applyAsDouble(node,next);
                if(cost>maximumCost || cost>=costs.getOrDefault(next,Double.POSITIVE_INFINITY))continue;
                if(!costs.containsKey(next) && costs.size()>=maximumNodes)continue;
                costs.put(next,cost);parents.put(next,node);open.add(new Entry<>(next,cost));
            }
        }
        return done;
    }
    List<T> route() { return route; }
}
