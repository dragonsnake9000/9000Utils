package dev.dragonsnake9000.utils;
import java.util.Deque;
import java.util.Objects;
/** Skip already passed waypoints, including loops in a recorded breadcrumb trail. */
final class ReturnRouteProgress {
    static <T> boolean advance(Deque<T> route,T feet) {
        int index=0,through=-1;
        for(T point:route){if(Objects.equals(point,feet))through=index;index++;}
        if(through<0)return false;
        for(int i=0;i<=through;i++)route.removeFirst();
        return true;
    }
}
