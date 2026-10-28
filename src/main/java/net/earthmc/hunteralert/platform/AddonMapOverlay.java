package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.detection.MapProjection;
import net.earthmc.hunteralert.model.Models.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.townymap.TownyMapMod;
import java.util.*;

/** Drawn after EarthMC map tiles and players, before its controls. Never creates a world waypoint. */
public final class AddonMapOverlay {
    public static void render(GuiGraphicsExtractor ctx,double cameraX,double cameraZ,double scale,int width,int height) {
        var mod=HunterAlertClient.INSTANCE;
        if(mod==null || !mod.active || !mod.store.config().enabled || !mod.store.config().mapHighlights || !TownyMapMod.isActiveOnCurrentServer() || TownyMapMod.isArchiveMode() || TownyMapMod.hideChromeForScreenshot()) return;
        if(mod.store.config().pathTrace) PathTraceRenderer.map(ctx,mod.view.get().approaches(),TownyMapMod.activeWorldKey(),cameraX,cameraZ,scale,width,height,System.currentTimeMillis(),mod.store.config().staleSeconds);
        draw(ctx,mod.view.get().markers(),TownyMapMod.activeWorldKey(),cameraX,cameraZ,scale,width,height,System.currentTimeMillis());
    }
    static void draw(GuiGraphicsExtractor ctx,List<ThreatMarker> markers,String world,double cameraX,double cameraZ,double scale,int width,int height,long now) {
        var font=Minecraft.getInstance().font;
        List<int[]> labels=new ArrayList<>();
        ctx.enableScissor(0,0,width,height);
        try {
            for(var marker:markers) {
                if(now>=marker.expiresAt()) continue;
                var point=MapProjection.project(marker.position(),world,cameraX,cameraZ,scale,width,height);
                if(point==null) continue;
                int x=point.x(),y=point.y(),color=marker.color();
                ctx.fill(x-5,y-5,x+6,y+6,0xEE111820);
                ctx.fill(x-4,y-4,x+5,y-2,color); ctx.fill(x-4,y+3,x+5,y+5,color);
                ctx.fill(x-4,y-2,x-2,y+3,color); ctx.fill(x+3,y-2,x+5,y+3,color);
                ctx.fill(x-1,y-1,x+2,y+2,color);
                String label=switch(marker.type()) {
                    case LIVE -> marker.identity().name()+" | "+marker.outlaws()+" outlaw towns";
                    case LAST_SEEN -> marker.identity().name()+" | last seen "+Math.max(0,(now-marker.observedAt())/1000)+"s";
                    case AFFILIATION -> "CLUE: "+marker.label();
                };
                label=font.plainSubstrByWidth(label,Math.max(30,Math.min(300,width-12)));
                int textWidth=font.width(label),left=Math.clamp(x+9,3,Math.max(3,width-textWidth-3)),top=Math.clamp(y-5,3,Math.max(3,height-13));
                for(int[] box:labels) if(left<box[2] && left+textWidth>box[0] && top<box[3] && top+12>box[1]) top=box[3]+2;
                if(top+12>=height) continue;
                ctx.fill(left-2,top-2,left+textWidth+2,top+10,0xDD111820);
                ctx.text(font,label,left,top,color,true);
                labels.add(new int[]{left-2,top-2,left+textWidth+2,top+10});
            }
        } finally { ctx.disableScissor(); }
    }
    private AddonMapOverlay() {}
}
