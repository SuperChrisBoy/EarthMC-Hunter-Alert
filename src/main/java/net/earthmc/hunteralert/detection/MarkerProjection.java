package net.earthmc.hunteralert.detection;

/** Projects an X/Z bearing at the viewer's elevation; this never invents a target's altitude. */
public final class MarkerProjection {
    public record Point(double x,double y) {}
    public static Point project(double dx,double dz,double yaw,double pitch,double verticalFov,int width,int height) {
        double angle=Math.toRadians(yaw),tilt=Math.toRadians(pitch);
        double forward=-dx*Math.sin(angle)+dz*Math.cos(angle),right=dx*Math.cos(angle)+dz*Math.sin(angle);
        double depth=forward*Math.cos(tilt);
        if(depth<=.01 || width<=0 || height<=0) return null;
        double focal=height/(2*Math.tan(Math.toRadians(Math.clamp(verticalFov,30,110))/2));
        double x=width/2.0+right*focal/depth,y=height/2.0-forward*Math.sin(tilt)*focal/depth;
        return x<8 || x>width-8 || y<8 || y>height-8 ? null : new Point(x,y);
    }
    private MarkerProjection() {}
}
