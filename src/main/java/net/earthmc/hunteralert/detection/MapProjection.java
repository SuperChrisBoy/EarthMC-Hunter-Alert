package net.earthmc.hunteralert.detection;

import net.earthmc.hunteralert.model.Models.Position;

/** EarthMC map coordinates and the addon's GUI-pixels-per-block viewport, including dimension scaling. */
public final class MapProjection {
    public record Point(int x,int y) {}
    public static Point project(Position position,String world,double cameraX,double cameraZ,double scale,int width,int height) {
        if(!position.world().equals(world) || !Double.isFinite(scale) || scale<=0 || width<=0 || height<=0) return null;
        double x=width/2.0+(position.x()-cameraX)*scale,y=height/2.0+(position.z()-cameraZ)*scale;
        if(!Double.isFinite(x) || !Double.isFinite(y) || x<0 || y<0 || x>=width || y>=height) return null;
        return new Point((int)Math.round(x),(int)Math.round(y));
    }
    private MapProjection() {}
}
