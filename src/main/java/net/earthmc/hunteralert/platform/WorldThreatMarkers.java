package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.detection.MarkerProjection;
import net.earthmc.hunteralert.model.Models.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;

/** Screen-projected public X/Z markers, independent of entities and optional map mods. */
public final class WorldThreatMarkers {
    public static void render(GuiGraphicsExtractor ctx,HunterAlertClient mod) {
        var client=Minecraft.getInstance(); var c=mod.store.config();
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("townymapaddon") || !mod.active || !c.worldMarkers || client.player==null || client.screen!=null || client.options.hideGui) return;
        Position local=mod.localMapPosition(client);
        if(local==null) return;
        if(c.pathTrace) PathTraceRenderer.world(ctx,mod.view.get().approaches(),local,client.gameRenderer.getMainCamera().yRot(),client.gameRenderer.getMainCamera().xRot(),client.options.fov().get(),System.currentTimeMillis(),c.staleSeconds);
        draw(ctx,mod.view.get().markers(),local,client.gameRenderer.getMainCamera().yRot(),client.gameRenderer.getMainCamera().xRot(),client.options.fov().get());
    }
    static void draw(GuiGraphicsExtractor ctx,List<ThreatMarker> markers,Position local,double yaw,double pitch,double fov) {
        var client=Minecraft.getInstance();
        long now=System.currentTimeMillis();
        List<int[]> labels=new ArrayList<>();
        for(ThreatMarker marker:markers.stream().filter(m -> m.type()!=MarkerType.AFFILIATION).limit(8).toList()) {
            if(now>=marker.expiresAt() || !local.world().equals(marker.position().world())) continue;
            var point=MarkerProjection.project(marker.position().x()-local.x(),marker.position().z()-local.z(),yaw,pitch,fov,ctx.guiWidth(),ctx.guiHeight());
            if(point==null) continue;
            int x=(int)point.x(),y=(int)point.y();
            String label=marker.identity().name()+" | "+Math.round(local.distance(marker.position()).orElse(0))+"m | "+(marker.type()==MarkerType.LIVE ? "MAP X/Z" : "LAST SEEN "+Math.max(0,(now-marker.observedAt())/1000)+"s");
            int width=client.font.width(label),left=Math.clamp(x-width/2,2,Math.max(2,ctx.guiWidth()-width-2));
            for(int[] box:labels) if(left<box[2] && left+width>box[0] && y<box[3] && y+12>box[1]) y=box[3]+2;
            if(y+18>=ctx.guiHeight()) continue;
            ctx.fill(x-2,(int)point.y()-2,x+3,(int)point.y()+3,marker.color());
            ctx.fill(left-2,y+5,left+width+2,y+17,0xBB111820);
            ctx.text(client.font,label,left,y+7,marker.color(),true);
            labels.add(new int[]{left,y,left+width,y+18});
        }
    }
    private WorldThreatMarkers() {}
}
