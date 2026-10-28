package net.earthmc.hunteralert.detection;

import net.earthmc.hunteralert.model.Models.Position;

/** Clip observed map segments before rasterization, including segments crossing the viewport. */
public final class TraceGeometry {
    public record Segment(double x1,double y1,double x2,double y2) {}
    public static Segment project(Position a,Position b,String world,double cameraX,double cameraZ,double scale,int width,int height) {
        if(!a.world().equals(world) || !b.world().equals(world) || !Double.isFinite(scale) || scale<=0) return null;
        return clip(width/2.0+(a.x()-cameraX)*scale,height/2.0+(a.z()-cameraZ)*scale,
            width/2.0+(b.x()-cameraX)*scale,height/2.0+(b.z()-cameraZ)*scale,width,height);
    }
    public static Segment clip(double x1,double y1,double x2,double y2,int width,int height) {
        if(width<=0 || height<=0 || !Double.isFinite(x1+y1+x2+y2)) return null;
        double dx=x2-x1,dy=y2-y1,lo=0,hi=1;
        if(!Double.isFinite(dx) || !Double.isFinite(dy)) return null;
        double[] p={-dx,dx,-dy,dy},q={x1,width-1-x1,y1,height-1-y1};
        for(int i=0;i<4;i++) {
            if(p[i]==0) { if(q[i]<0) return null; }
            else { double r=q[i]/p[i]; if(p[i]<0) lo=Math.max(lo,r); else hi=Math.min(hi,r); if(lo>hi) return null; }
        }
        return new Segment(x1+lo*dx,y1+lo*dy,x1+hi*dx,y1+hi*dy);
    }
    private TraceGeometry() {}
}
