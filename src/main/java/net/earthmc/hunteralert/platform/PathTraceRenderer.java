package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.model.Models.*;
import net.earthmc.hunteralert.detection.*;
import net.minecraft.client.gui.DrawContext;
import java.util.*;

/** Bounded observed paths, clipped to the viewport; never extrapolates toward the user. */
public final class PathTraceRenderer {
    public static void map(DrawContext ctx,List<Approach> paths,String world,double x,double z,double scale,int width,int height,long now,int staleSeconds) {
        int budget=4096;
        ctx.enableScissor(0,0,width,height);
        try {
            for(var path:paths.stream().filter(Approach::hunter).limit(4).toList()) {
                if(now<path.observedAt() || now-path.observedAt()>staleSeconds*1000L) continue;
                var points=path.path();
                for(int i=1;i<points.size() && budget>0;i++) {
                    var segment=TraceGeometry.project(points.get(i-1),points.get(i),world,x,z,scale,width,height);
                    budget-=line(ctx,segment,budget,0xFFEE8844);
                    if(i==points.size()-1 && segment!=null) arrow(ctx,segment,width,height);
                }
            }
        } finally { ctx.disableScissor(); }
    }
    public static void world(DrawContext ctx,List<Approach> paths,Position local,double yaw,double pitch,double fov,long now,int staleSeconds) {
        int width=ctx.getScaledWindowWidth(),height=ctx.getScaledWindowHeight(),budget=2048;
        for(var path:paths.stream().filter(Approach::hunter).limit(4).toList()) {
            if(now<path.observedAt() || now-path.observedAt()>staleSeconds*1000L) continue;
            var points=path.path();
            for(int i=1;i<points.size() && budget>0;i++) {
                var a=points.get(i-1); var b=points.get(i);
                if(!a.world().equals(local.world()) || !b.world().equals(local.world())) continue;
                var pa=MarkerProjection.project(a.x()-local.x(),a.z()-local.z(),yaw,pitch,fov,width,height);
                var pb=MarkerProjection.project(b.x()-local.x(),b.z()-local.z(),yaw,pitch,fov,width,height);
                if(pa==null || pb==null) continue;
                var segment=TraceGeometry.clip(pa.x(),pa.y(),pb.x(),pb.y(),width,height);
                budget-=line(ctx,segment,budget,0xFFEE8844);
                if(i==points.size()-1 && segment!=null) arrow(ctx,segment,width,height);
            }
        }
    }
    private static int line(DrawContext ctx,TraceGeometry.Segment s,int budget,int color) {
        if(s==null || budget<=0) return 0;
        int steps=Math.min(budget,Math.max(1,(int)Math.ceil(Math.max(Math.abs(s.x2()-s.x1()),Math.abs(s.y2()-s.y1())))));
        for(int i=0;i<steps;i++) {
            double t=steps==1 ? 1 : i/(double)(steps-1);
            int x=(int)Math.round(s.x1()+(s.x2()-s.x1())*t),y=(int)Math.round(s.y1()+(s.y2()-s.y1())*t);
            ctx.fill(x,y,x+1,y+1,color);
        }
        return steps;
    }
    private static void arrow(DrawContext ctx,TraceGeometry.Segment s,int width,int height) {
        double dx=s.x2()-s.x1(),dy=s.y2()-s.y1(),length=Math.hypot(dx,dy);
        if(length<3) return;
        dx/=length; dy/=length;
        for(int side:new int[]{-1,1}) line(ctx,TraceGeometry.clip(s.x2(),s.y2(),s.x2()-dx*6+dy*side*3,s.y2()-dy*6-dx*side*3,width,height),16,0xFFFFFFAA);
    }
    private PathTraceRenderer() {}
}
