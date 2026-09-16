package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.integration.map.ThreatMapLayer;
import net.earthmc.hunteralert.model.Models.*;
import net.minecraft.client.Minecraft;
import net.townymap.TownyMapMod;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import java.util.*;

/** Temporary Xaero waypoints shared by the addon's map overlays and in-world waypoint renderer. */
public final class AddonThreatMapLayer implements ThreatMapLayer {
    private WaypointSet owner;
    private xaero.hud.minimap.module.MinimapSession ownerSession;
    private final List<Waypoint> owned=new ArrayList<>();
    private long refreshed;
    @Override public void publish(List<ThreatMarker> markers,Position local,double radius) {
        long now=System.currentTimeMillis();
        var client=Minecraft.getInstance();
        var session=BuiltInHudModules.MINIMAP.getCurrentSession();
        if(client.player==null || session==null || session.getWorldManager()==null || local==null) { clear(); return; }
        var world=session.getWorldManager().getCurrentWorld();
        var set=world==null ? null : world.getCurrentWaypointSet();
        if(set==null || TownyMapMod.isArchiveMode()) { clear(); return; }
        if(set==owner && now-refreshed<1000) return;
        double scale=TownyMapMod.dimensionCoordinateScale();
        replace(set,markers,local,scale,client.player.blockPosition().getY(),now);
        ownerSession=session;
        session.getWaypointSession().setSetChangedTime(now);
    }
    private void replace(WaypointSet set,List<ThreatMarker> markers,Position local,double scale,int y,long now) {
        clear(); owner=set; refreshed=now;
        for(ThreatMarker marker:markers.stream().limit(32).toList()) {
            if(now>=marker.expiresAt() || !marker.position().world().equals(local.world())) continue;
            String label=switch(marker.type()) { case LIVE -> "PUBLIC MAP: "; case LAST_SEEN -> "LAST SEEN: "; case AFFILIATION -> "CLUE: "; };
            label+="HA "+marker.label()+" ("+marker.outlaws()+" outlaws)";
            var color=marker.type()==MarkerType.AFFILIATION ? WaypointColor.GOLD : marker.type()==MarkerType.LAST_SEEN ? WaypointColor.GRAY : WaypointColor.RED;
            var waypoint=new Waypoint((int)Math.round(marker.position().x()/scale),y,(int)Math.round(marker.position().z()/scale),label,marker.type()==MarkerType.AFFILIATION ? "?" : "!",color,WaypointPurpose.NORMAL,true,false);
            owner.add(waypoint,true); owned.add(waypoint);
        }
    }
    @Override public void clear() {
        if(owner!=null) for(Waypoint waypoint:owned) owner.remove(waypoint);
        if(ownerSession!=null && ownerSession.getWaypointSession()!=null) ownerSession.getWaypointSession().setSetChangedTime(System.currentTimeMillis());
        ownerSession=null;
        owned.clear(); owner=null;
    }
    static void smokeCheck() {
        var set=WaypointSet.Builder.begin().setDefault().setName("HunterAlert isolated fixture").build();
        var manual=new Waypoint(1,64,2,"User waypoint","U",WaypointColor.GOLD,WaypointPurpose.NORMAL,false,true);
        set.add(manual,true);
        var identity=new Identity(null,"FixtureHunter"); var local=new Position("earth",0,0);
        List<ThreatMarker> markers=new ArrayList<>();
        for(var type:MarkerType.values()) markers.add(new ThreatMarker(type.name(),identity,new Position("earth",800,1600),type,"Fixture town / player",35,1000,10_000));
        markers.add(new ThreatMarker("wrong-world",identity,new Position("moon",1,2),MarkerType.LIVE,"Moon",35,1000,10_000));
        var layer=new AddonThreatMapLayer(); layer.replace(set,markers,local,8,64,2000);
        if(set.size()!=4 || layer.owned.get(0).getX()!=100 || layer.owned.get(0).getZ()!=200 || !layer.owned.get(0).isTemporary() || layer.owned.get(0).isYIncluded() || !layer.owned.get(2).getName().startsWith("CLUE:")) throw new AssertionError("Temporary marker normalization failed");
        layer.clear(); if(set.size()!=1 || set.get(0)!=manual) throw new AssertionError("Marker cleanup touched a user waypoint");
        layer.replace(set,markers,local,1,64,11_000); if(set.size()!=1) throw new AssertionError("Expired markers persisted"); layer.clear();
        org.slf4j.LoggerFactory.getLogger("HunterAlertSmoke").info("THREAT_WAYPOINT_PASS: live/last-seen/clue, dimension scale, expiry, user waypoint preservation");
    }
}
