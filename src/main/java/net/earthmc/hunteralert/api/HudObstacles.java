package net.earthmc.hunteralert.api;

import java.util.*;

/** GUI-pixel rectangles. Other HUD mods may publish their own occupied area each frame. */
public final class HudObstacles {
    public record Rect(int x,int y,int width,int height) {
        public int right() { return x+width; }
        public int bottom() { return y+height; }
        public boolean overlaps(Rect b) { return x<b.right() && right()>b.x && y<b.bottom() && bottom()>b.y; }
        Rect union(Rect b) { int left=Math.min(x,b.x),top=Math.min(y,b.y); return new Rect(left,top,Math.max(right(),b.right())-left,Math.max(bottom(),b.bottom())-top); }
    }
    private record Entry(Rect rect,long at) {}
    private static final Map<String,Entry> entries=new HashMap<>();
    private static int depth;
    private static Rect captured;
    public static synchronized void publish(String id,Rect bounds) { entries.put(id,new Entry(bounds,System.nanoTime())); }
    public static synchronized List<Rect> snapshot() {
        long now=System.nanoTime(); entries.values().removeIf(e -> now-e.at>1_000_000_000L);
        return entries.values().stream().map(Entry::rect).toList();
    }
    public static void begin() { if(depth++==0) captured=null; }
    public static boolean capturing() { return depth>0; }
    public static void include(Rect rect) { if(capturing()) captured=captured==null ? rect : captured.union(rect); }
    public static void end() { if(depth>0 && --depth==0 && captured!=null) publish("xaero",captured); }
    /** Closest free location to the preferred position; null means no free space at this size. */
    public static Rect place(int screenW,int screenH,int width,int height,int preferredX,int preferredY,List<Rect> occupied) {
        if(width>screenW || height>screenH) return null;
        int px=Math.clamp(preferredX,0,screenW-width),py=Math.clamp(preferredY,0,screenH-height);
        Set<Integer> xs=new LinkedHashSet<>(List.of(px,0,screenW-width));
        Set<Integer> ys=new LinkedHashSet<>(List.of(py,0,screenH-height));
        for(Rect r:occupied) { xs.add(r.right()+6); xs.add(r.x-width-6); ys.add(r.bottom()+6); ys.add(r.y-height-6); }
        Rect best=null; long score=Long.MAX_VALUE;
        for(int x:xs) for(int y:ys) {
            Rect candidate=new Rect(x,y,width,height);
            if(x<0 || y<0 || candidate.right()>screenW || candidate.bottom()>screenH) continue;
            Rect padded=new Rect(x-4,y-4,width+8,height+8);
            if(occupied.stream().anyMatch(padded::overlaps)) continue;
            long distance=(long)(x-px)*(x-px)+(long)(y-py)*(y-py);
            if(distance<score) { score=distance; best=candidate; }
        }
        return best;
    }
}
