package net.earthmc.hunteralert;

import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.detection.*;
import net.earthmc.hunteralert.data.OutlawIndex;
import net.earthmc.hunteralert.model.Models.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ThreatEngineTest {
    static final Identity SELF=new Identity(UUID.fromString("00000000-0000-0000-0000-000000000001"),"Self");
    static final Identity HUNTER=new Identity(UUID.fromString("00000000-0000-0000-0000-000000000002"),"Hunter");
    static final Position ORIGIN=new Position("terra",0,0);
    HunterAlertConfig config() { var c=new HunterAlertConfig(); c.tracked.add(new HunterAlertConfig.Tracked(HUNTER,Kind.KNOWN,"",true)); c.cooldownSeconds=0; return c; }
    MapPlayer player(double x,String world,long at) { return new MapPlayer(HUNTER,new Position(world,x,0),at); }
    Snapshot snapshot(long at,MapPlayer... players) { return new Snapshot(new Feed(at,0,true,List.of(players)),List.of(),Map.of(),Map.of(),at); }
    View step(ThreatEngine e,HunterAlertConfig c,long time,double x) { return e.update(snapshot(time,player(x,"terra",time)),SELF,ORIGIN,time,c); }

    @Test void distanceAndDirections() {
        assertEquals(0,ORIGIN.distance(ORIGIN).orElseThrow());
        assertEquals(5,ORIGIN.distance(new Position("terra",3,4)).orElseThrow());
        assertEquals(5,new Position("terra",-3,-4).distance(ORIGIN).orElseThrow());
        assertEquals("NW",ORIGIN.direction(new Position("terra",-20,-20)));
        assertEquals("S",ORIGIN.direction(new Position("terra",0,2)));
        assertTrue(ORIGIN.distance(new Position("moon",0,0)).isEmpty());
        assertTrue(new Position("",0,0).distance(new Position("",0,0)).isEmpty());
    }
    @Test void radiusTransitionsAndNoDuplicates() {
        var e=new ThreatEngine(); var c=config();
        assertTrue(step(e,c,1000,6000).events().isEmpty());
        assertTrue(step(e,c,2000,2500).events().stream().anyMatch(a -> a.key().endsWith("entered-awareness")));
        assertTrue(step(e,c,3000,1400).events().stream().anyMatch(a -> a.key().endsWith("entered-warning")));
        assertTrue(step(e,c,4000,400).events().stream().anyMatch(a -> a.key().endsWith("entered-critical")));
        assertTrue(step(e,c,5000,6000).events().stream().anyMatch(a -> a.key().endsWith("left-area")));
        assertTrue(step(e,c,6000,6000).events().isEmpty());
        var fixed=snapshot(7000,player(1400,"terra",7000));
        e.update(fixed,SELF,ORIGIN,7000,c);
        assertTrue(e.update(fixed,SELF,ORIGIN,7001,c).events().isEmpty());
    }
    @Test void outlawThresholdAndPriority() {
        for(int count:List.of(9,10,11)) {
            var c=new HunterAlertConfig(); var p=player(1000,"terra",1000);
            var s=new Snapshot(snapshot(1000,p).feed(),List.of(),Map.of(),Map.of("hunter",count),1000);
            var view=new ThreatEngine().update(s,SELF,ORIGIN,1000,c);
            assertEquals(count>=10?Kind.AUTOMATIC:Kind.ORDINARY,view.nearby().getFirst().kind());
        }
        assertEquals(Kind.KNOWN,ThreatEngine.classify(true,true,true));
        assertEquals(Kind.AUTOMATIC,ThreatEngine.classify(false,true,true));
        assertEquals(Map.of("hunter",12),OutlawIndex.normalize(Map.of("HuNtEr",new int[]{12,0})));
    }
    @Test void exposureTransitionsAndFailure() {
        var e=new ThreatEngine(); var c=config(); c.hiddenAlert=true;
        var me=new MapPlayer(SELF,ORIGIN,1000);
        assertEquals(Visibility.EXPOSED,e.update(snapshot(1000,me),SELF,ORIGIN,1000,c).exposure().state());
        View hidden=e.update(snapshot(2000),SELF,ORIGIN,2000,c);
        assertEquals(Visibility.HIDDEN,hidden.exposure().state());
        assertEquals(2000,hidden.exposure().hiddenSince());
        assertEquals(Visibility.EXPOSED,e.update(snapshot(3000,new MapPlayer(SELF,ORIGIN,3000)),SELF,ORIGIN,3000,c).exposure().state());
        var failed=new Snapshot(new Feed(4000,0,false,List.of()),List.of(),Map.of(),Map.of(),0);
        View unknown=e.update(failed,SELF,ORIGIN,4000,c);
        assertEquals(Visibility.UNKNOWN,unknown.exposure().state()); assertTrue(unknown.events().isEmpty());
    }
    @Test void staleFeedNeverMeansHiddenOrDeparture() {
        var e=new ThreatEngine(); var c=config();
        e.update(snapshot(1000,new MapPlayer(SELF,ORIGIN,1000),player(1000,"terra",1000)),SELF,ORIGIN,1000,c);
        View stale=e.update(snapshot(1000),SELF,ORIGIN,30_000,c);
        assertFalse(stale.healthy()); assertEquals(Visibility.UNKNOWN,stale.exposure().state()); assertTrue(stale.events().isEmpty());
    }
    @Test void movementApproachingAwayStationaryJitterAndStale() {
        var c=config(); var m=new MovementTracker();
        assertEquals(Trend.UNKNOWN,m.update(player(1000,"terra",1000),ORIGIN,1000,c).trend());
        assertEquals(Trend.APPROACHING,m.update(player(900,"terra",6000),ORIGIN,6000,c).trend());
        m.clear(); m.update(player(1000,"terra",1000),ORIGIN,1000,c);
        assertEquals(Trend.MOVING_AWAY,m.update(player(1100,"terra",6000),ORIGIN,6000,c).trend());
        m.clear(); m.update(player(1000,"terra",1000),ORIGIN,1000,c);
        assertEquals(Trend.STATIONARY,m.update(player(1000,"terra",6000),ORIGIN,6000,c).trend());
        assertEquals(Trend.STATIONARY,m.update(player(1004,"terra",8000),ORIGIN,8000,c).trend());
        assertEquals(Trend.UNKNOWN,m.update(player(1004,"terra",8000),ORIGIN,30_000,c).trend());
    }
    @Test void localMovementDoesNotBecomeHunterMotion() {
        var m=new MovementTracker(); var c=config(); m.update(player(1000,"terra",1000),ORIGIN,1000,c);
        assertEquals(Trend.STATIONARY,m.update(player(1000,"terra",5000),new Position("terra",500,0),5000,c).trend());
    }
    @Test void crossWorldCannotProduceProximity() {
        var view=new ThreatEngine().update(snapshot(1000,player(1,"moon",1000)),SELF,ORIGIN,1000,config());
        assertTrue(view.nearby().isEmpty()); assertTrue(view.events().isEmpty());
    }
    @Test void watchedToggleAndKnownHunterIgnoreOverride() {
        var c=new HunterAlertConfig(); c.watchedAlerts=false; c.tracked.add(new HunterAlertConfig.Tracked(HUNTER,Kind.WATCHED,"",true));
        assertTrue(step(new ThreatEngine(),c,1000,200).events().isEmpty());
        c=config(); c.ignoreTown=true;
        var s=new Snapshot(snapshot(1000,player(200,"terra",1000)).feed(),List.of(),Map.of("self",new Profile(SELF,"Town","Nation",1000),"hunter",new Profile(HUNTER,"Town","Nation",1000)),Map.of(),1000);
        assertEquals(1,new ThreatEngine().update(s,SELF,ORIGIN,1000,c).nearby().size());
    }
    @Test void affiliationIsNotPhysicalLocationAndUsesNearestNationTown() {
        var e=new ThreatEngine(); var c=config();
        var towns=List.of(new Town("Kyoto","Japan",new Position("terra",1600,0),"spawn"),new Town("MoonTown","Japan",new Position("moon",1,0),"spawn"));
        var first=new Snapshot(snapshot(1000).feed(),towns,Map.of("hunter",new Profile(HUNTER,"","",1000)),Map.of(),1000);
        e.update(first,SELF,ORIGIN,1000,c);
        var changed=new Snapshot(snapshot(2000).feed(),towns,Map.of("hunter",new Profile(HUNTER,"Kyoto","Japan",2000)),Map.of(),2000);
        View result=e.update(changed,SELF,ORIGIN,2000,c);
        assertTrue(result.nearby().isEmpty()); assertEquals(2,result.events().size());
        assertTrue(result.events().stream().allMatch(a -> a.title().equals("AFFILIATION WARNING") && a.message().contains("1600") && a.message().contains("location unconfirmed")));
        assertTrue(e.update(changed,SELF,ORIGIN,2001,c).events().isEmpty());
    }
    @Test void identityUsesUuidAndSupportsNameFallback() {
        assertTrue(HUNTER.matches(new Identity(HUNTER.uuid(),"Renamed")));
        assertTrue(HUNTER.matches(new Identity(null,"HUNTER")));
        assertFalse(HUNTER.matches(new Identity(SELF.uuid(),"Hunter")));
        assertEquals(HUNTER,Identity.of(HUNTER.uuid().toString().replace("-",""),"Hunter"));
    }
    @Test void configClampsInvalidBounds() {
        var c=config(); c.critical=Double.NaN; c.warning=-1; c.maximum=2; c.opacity=3; c.historyLimit=Integer.MAX_VALUE; c.validate();
        assertEquals(500,c.critical); assertTrue(c.warning>=c.critical); assertTrue(c.maximum>=c.awareness); assertEquals(1,c.opacity); assertEquals(2000,c.historyLimit);
    }
}
