package com.lamggm.rospocket;
import java.util.ArrayDeque;
/** Observed positions only; UI-thread owned, finite and bounded, never persisted. */
final class LocationTrail {
 static final int CAPACITY=256;
 static final class Point {final double x,y;final boolean start;Point(double x,double y,boolean start){this.x=x;this.y=y;this.start=start;}}
 private final ArrayDeque<Point> points=new ArrayDeque<>();private boolean enabled=true,split=true;
 void enabled(boolean on){enabled=on;if(!on)clear();}
 void clear(){points.clear();split=true;}
 void gap(){split=true;}
 void add(double x,double y){if(!enabled||!Double.isFinite(x)||!Double.isFinite(y))return;Point p=points.peekLast();if(!split&&p!=null&&p.x==x&&p.y==y)return;points.addLast(new Point(x,y,split));split=false;while(points.size()>CAPACITY)points.removeFirst();}
 int size(){return points.size();}
 int segments(){int n=0;for(Point p:points)if(n==0||p.start)n++;return n;}
 Iterable<Point> points(){return points;}
}
