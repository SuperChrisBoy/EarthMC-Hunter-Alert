package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.model.Models.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.util.*;
import net.earthmc.hunteralert.detection.ThreatPresentation;
import net.earthmc.hunteralert.api.HudObstacles;

/** Tick-prepared legacy content with responsive wrapping and pagination at render time. */
public final class HunterHud {
    private record Line(String text,int color,boolean divider) {
        Line(String text,int color) { this(text,color,false); }
    }
    private final HunterAlertClient mod;
    private List<Line> lines=List.of();
    private int width=220;
    private long refreshed;
    private long pageStarted;
    private String pageKey="";
    public HunterHud(HunterAlertClient mod) { this.mod=mod; }
    public void refresh(long now) {
        if(now-refreshed < 250) return;
        refreshed=now;
        var c=mod.store.config(); var view=mod.view.get(); var e=view.exposure();
        List<Line> next=new ArrayList<>();
        if(mod.raining && c.rainAlerts) next.add(new Line("RAIN: fast Riptide travel | wilderness risk",0xFFFFCC55));
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
        var font=MinecraftClient.getInstance().textRenderer;
        width=lines.stream().mapToInt(l -> font.getWidth(l.text())).max().orElse(160)+16;
    }
    public void render(DrawContext ctx) {
        var c=mod.store.config(); var client=MinecraftClient.getInstance();
        if(!mod.active || !c.hud || client.options.hudHidden) return;
        int screenW=ctx.getScaledWindowWidth(),screenH=ctx.getScaledWindowHeight();
        double scale=c.hudScale;
        long now=System.currentTimeMillis();
        List<Line> fixed=new ArrayList<>(), pinned=new ArrayList<>(), content=new ArrayList<>();
        String title="HUNTER ALERT";
        if(c.logView) {
            title="HUNTER LOG"+(mod.raining && c.rainAlerts ? " | RAIN" : "")+(!mod.view.get().healthy() ? " | map stale" : "");
            var exposure=mod.view.get().exposure();
            long seconds=Math.max(0,(now-exposure.since())/1000);
            String timer=String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
            fixed.add(new Line(switch(exposure.state()) {
                case EXPOSED -> "Dynmap: EXPOSED " + timer;
                case HIDDEN -> "Dynmap: HIDDEN " + timer;
                case UNKNOWN -> "Dynmap: UNKNOWN";
            },exposure.state()==Visibility.EXPOSED ? 0xFFFFFF55 : exposure.state()==Visibility.HIDDEN ? 0xFF77DD99 : 0xFFBBBBBB,true));
            var awareness=net.earthmc.hunteralert.detection.AwarenessHud.rows(mod.view.get(),c,now);
            for(var hunter:awareness) {
                int color=hunter.stale()?0xFFCCCCCC:0xFFFFBB66;
                pinned.add(new Line(hunter.identity(),color,true));
                pinned.add(new Line(hunter.location(),color));
            }
            for(var entry:mod.alertLog.recent(now,c.logSeconds,c.logRows))
                content.add(new Line(Math.max(0,(now-entry.at())/1000)+"s "+entry.text(),entry.priority()>=2 ? 0xFFFFBB66 : 0xFFDDDDDD,true));
            if(pinned.isEmpty() && content.isEmpty()) content.add(new Line("No recent alerts",0xFFCCCCCC));
        } else content.addAll(lines);
        List<HudObstacles.Rect> occupied=new ArrayList<>(c.avoidHudOverlap ? (c.logView ? HudObstacles.logObstacles(screenH) : HudObstacles.snapshot()) : List.of());
        if(c.avoidHudOverlap) occupied.add(new HudObstacles.Rect(Math.max(0,screenW/2-100),Math.max(0,screenH-65),200,65));
        int preferredWidth=Math.max(40,(int)(Math.min(360,screenW*.48)/scale));
        int panelWidth=preferredWidth;
        List<Line> display=List.of();
        HudObstacles.Rect placement=null;
        String key=c.logView+":"+screenW+":"+screenH+":"+scale;
        if(!key.equals(pageKey)) { pageKey=key; pageStarted=now; }
        // Try readable widths and fewer visible rows before surrendering overlap avoidance.
        // Pagination preserves all text even when only a few rows fit beside another HUD.
        for(int candidate=0;candidate<3 && placement==null;candidate++) {
            panelWidth=Math.min((int)(screenW/scale),Math.max(40,preferredWidth-candidate*preferredWidth/5));
            int available=Math.max(1,panelWidth-16);
            var wrappedFixed=wrap(fixed,available,client.textRenderer::getWidth);
            var wrappedPinned=wrap(pinned,available,client.textRenderer::getWidth);
            var wrappedContent=wrap(content,available,client.textRenderer::getWidth);
            int maxRows=Math.max(2,(int)(screenH*.4/scale/12)-1);
            for(int rows=maxRows;rows>=2;rows--) {
                // Reserve enough header space for the page indicator before choosing the body.
                var header=net.earthmc.hunteralert.detection.WrappedHud.wrap(title+" | 99/99",available,client.textRenderer::getWidth);
                int capacity=rows-header.size()-wrappedFixed.size();
                if(capacity<1) continue;
                var page=net.earthmc.hunteralert.detection.WrappedHud.page(wrappedPinned,wrappedContent,capacity,now-pageStarted);
                List<Line> proposed=new ArrayList<>();
                for(String line:net.earthmc.hunteralert.detection.WrappedHud.wrap(title+(page.count()>1?" | "+page.number()+"/"+page.count():""),available,client.textRenderer::getWidth)) proposed.add(new Line(line,0xFFFFFFFF));
                proposed.addAll(wrappedFixed);
                proposed.addAll(page.lines());
                placement=HudObstacles.place(screenW,screenH,(int)Math.ceil(panelWidth*scale),(int)Math.ceil((proposed.size()*12+12)*scale),(int)(c.hudX*c.hudScale),(int)(c.hudY*c.hudScale),occupied);
                if(placement!=null) { display=proposed; break; }
            }
        }
        if(placement==null) return;
        ctx.getMatrices().pushMatrix(); ctx.getMatrices().translate(placement.x(),placement.y()); ctx.getMatrices().scale((float)scale,(float)scale);
        int x=0,y=0;
        int color=((int)(255*Math.max(c.logView?.92:0,c.opacity))<<24)|0x111820;
        ctx.fill(x,y,x+panelWidth,y+display.size()*12+12,color);
        if(now<mod.flashUntil) ctx.fill(x,y,x+2,y+display.size()*12+12,0xFFFFAA55);
        int row=y+6;
        for(Line line:display) { if(line.divider()) ctx.fill(x+8,row-2,x+panelWidth-8,row-1,0xFF596773); ctx.drawText(client.textRenderer,line.text(),x+8,row,line.color(),true); row+=12; }
        ctx.getMatrices().popMatrix();
    }
    private static List<Line> wrap(List<Line> source,int width,java.util.function.ToIntFunction<String> measure) {
        List<Line> result=new ArrayList<>();
        for(var line:source) {
            boolean first=true;
            for(String part:net.earthmc.hunteralert.detection.WrappedHud.wrap(line.text(),width,measure)) {
                result.add(new Line(part,line.color(),first && line.divider()));
                first=false;
            }
        }
        return result;
    }

}
