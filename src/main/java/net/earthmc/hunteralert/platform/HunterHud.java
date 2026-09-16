package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.model.Models.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;
import net.earthmc.hunteralert.detection.ThreatPresentation;
import net.earthmc.hunteralert.api.HudObstacles;

/** All strings, sorting and layout are prepared on ticks; render only draws cached lines. */
public final class HunterHud {
    private record Line(String text,int color) {}
    private final HunterAlertClient mod;
    private List<Line> lines=List.of();
    private int width=220;
    private long refreshed;
    public HunterHud(HunterAlertClient mod) { this.mod=mod; }
    public void refresh(long now) {
        if(now-refreshed < 250) return;
        refreshed=now;
        var c=mod.store.config(); var view=mod.view.get(); var e=view.exposure();
        List<Line> next=new ArrayList<>();
        String status=switch(e.state()) { case EXPOSED -> "MAP EXPOSED"; case HIDDEN -> "MAP HIDDEN"; case UNKNOWN -> "MAP STATUS UNKNOWN"; };
        status="EXPOSURE "+Math.round(e.percent())+"% | "+status+(e.state()==Visibility.UNKNOWN ? " (paused)" : "");
        long seconds=Math.max(0,(now-e.since())/1000);
        if(c.exposureTimer && e.state() != Visibility.UNKNOWN) status+=" "+String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
        long threats=view.nearby().stream().filter(a -> a.kind().priority >= 2).count();
        int exposureColor=e.state()==Visibility.EXPOSED ? 0xFFFFAA55 : e.state()==Visibility.HIDDEN ? 0xFF77DD99 : 0xFFBBBBBB;
        var leader=view.nearby().stream().filter(a -> a.kind().priority>=2).sorted(ThreatPresentation.ORDER).findFirst();
        leader.ifPresent(a -> next.add(new Line((a.stale()?"LAST SEEN":a.radius().name())+": "+a.player().identity().name()+" | "+a.outlawCount()+" outlaw towns",ThreatPresentation.color(a))));
        for(var marker:view.markers().stream().filter(m -> m.type()==MarkerType.AFFILIATION && now<m.expiresAt()).limit(2).toList()) next.add(new Line("POSSIBLE RISK: "+marker.label(),marker.color()));
        if(leader.isEmpty() && view.markers().stream().anyMatch(m -> m.type()==MarkerType.LAST_SEEN && now<m.expiresAt())) next.add(new Line("Hunter left map; current location unknown",0xFFFFCC55));
        if(c.compact) next.add(new Line((c.showExposure ? status+" | " : "")+(!view.healthy() || !view.locationKnown() ? "UNKNOWN" : threats==0 ? "NO MAP THREATS" : threats+" THREATS"),exposureColor));
        else {
            next.add(new Line("HUNTER ALERT",0xFFFFFFFF));
            if(c.showExposure) next.add(new Line(status,exposureColor));
            if(!view.healthy()) next.add(new Line("Map data unavailable",0xFFFFAA55));
            else if(!view.locationKnown()) next.add(new Line("Local map position unknown",0xFFFFAA55));
            else {
                for(Assessment a:view.nearby().stream().limit(c.maxEntries).toList()) {
                    String mark=switch(a.kind()) { case KNOWN -> "[H] "; case AUTOMATIC -> "[A] "; case WATCHED -> "[W] "; default -> ""; };
                    String distance=a.distance() >= 1000 ? String.format(Locale.ROOT,"%.2f km",a.distance()/1000) : Math.round(a.distance())+" blocks";
                    next.add(new Line(mark+a.player().identity().name()+"  "+distance+" "+a.direction()+(a.stale()?" (stale)":""),ThreatPresentation.color(a)));
                    if(a.kind().priority>=2) {
                        next.add(new Line("  "+a.outlawCount()+" outlaw towns | "+a.trend().name().replace('_',' '),0xFFAAAAAA));
                        if(a.sampleMillis()>0 && !a.stale()) next.add(new Line("  "+Math.round(a.distanceChange())+" blocks / "+a.sampleMillis()/1000+" sec",0xFFAAAAAA));
                    }
                    if(a.stale()) next.add(new Line("  Last update "+Math.max(0,(now-a.lastSeenAt())/1000)+" sec ago",0xFFAAAAAA));
                }
                next.add(new Line(view.nearby().size()+" nearby | "+threats+" threats",0xFFBBBBBB));
                if(view.nearby().isEmpty()) next.add(new Line("No public map players nearby",0xFF77DD99));
            }
        }
        lines=List.copyOf(next);
        var font=Minecraft.getInstance().font;
        width=lines.stream().mapToInt(l -> font.width(l.text())).max().orElse(160)+16;
    }
    public void render(GuiGraphicsExtractor ctx) {
        var c=mod.store.config(); var client=Minecraft.getInstance();
        if(!mod.active || !c.hud || client.options.hideGui) return;
        int screenW=ctx.guiWidth(),screenH=ctx.guiHeight();
        double scale=c.hudScale;
        List<HudObstacles.Rect> occupied=new ArrayList<>(c.avoidHudOverlap ? HudObstacles.snapshot() : List.of());
        if(c.avoidHudOverlap) occupied.add(new HudObstacles.Rect(Math.max(0,screenW/2-100),Math.max(0,screenH-65),200,65));
        HudObstacles.Rect placement=null;
        while(scale>=.49) {
            placement=HudObstacles.place(screenW,screenH,(int)Math.ceil(width*scale),(int)Math.ceil((lines.size()*12+12)*scale),(int)(c.hudX*c.hudScale),(int)(c.hudY*c.hudScale),occupied);
            if(placement!=null) break;
            scale-=.05;
        }
        if(placement==null) return;
        ctx.pose().pushMatrix(); ctx.pose().translate(placement.x(),placement.y()); ctx.pose().scale((float)scale,(float)scale);
        int x=0,y=0;
        int color=((int)(255*c.opacity)<<24)|(System.currentTimeMillis()<mod.flashUntil ? 0x772222 : 0x111820);
        ctx.fill(x,y,x+width,y+lines.size()*12+12,color);
        int row=y+6;
        for(Line line:lines) { ctx.text(client.font,line.text(),x+8,row,line.color(),true); row+=12; }
        ctx.pose().popMatrix();
    }
}
