package net.earthmc.hunteralert;

import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.data.PlayerSuggestions;
import net.earthmc.hunteralert.detection.ThreatEngine;
import net.earthmc.hunteralert.model.Models.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static net.earthmc.hunteralert.ThreatEngineTest.*;

class ThreatFeaturesTest {
    Snapshot snapshot(long at,double distance,int outlaws) {
        return new Snapshot(new Feed(at,0,true,List.of(new MapPlayer(HUNTER,new Position("terra",distance,0),at))),List.of(),Map.of(),Map.of("hunter",outlaws),at);
    }
    @Test void ordinaryPlayerApproachAlertsWithoutOrdinaryProximityAlerts() {
        var e=new ThreatEngine(); var c=new HunterAlertConfig(); c.approachInstances=1; c.approachRange=3000;
        assertEquals(150,c.approachingThreshold);
        assertTrue(e.update(snapshot(1000,2000,0),SELF,ORIGIN,1000,c).events().isEmpty());
        assertTrue(e.update(snapshot(4000,1851,0),SELF,ORIGIN,4000,c).events().isEmpty());
        var v=e.update(snapshot(7000,1800,0),SELF,ORIGIN,7000,c);
        assertEquals(1,v.events().size()); assertEquals("PLAYER APPROACHING",v.events().getFirst().title());
        assertTrue(v.events().getFirst().message().contains("200 blocks toward you"));
        assertTrue(e.update(snapshot(8000,1800,0),SELF,ORIGIN,8000,c).events().isEmpty());
    }
    @Test void approachToggleAndWhitelistSuppressApproach() {
        for(boolean friendly:List.of(false,true)) {
            var c=new HunterAlertConfig(); c.approachingPlayers=false; c.ordinaryAlerts=true;
            if(friendly) c.tracked.add(new HunterAlertConfig.Tracked(HUNTER,Kind.FRIENDLY,"",true));
            var e=new ThreatEngine(); int outlaws=friendly ? 50 : 0;
            e.update(snapshot(1000,2000,outlaws),SELF,ORIGIN,1000,c);
            assertTrue(e.update(snapshot(5000,1800,outlaws),SELF,ORIGIN,5000,c).events().isEmpty());
        }
    }
    @Test void highOutlawApproachIsExplicitlyPotentialHunter() {
        var c=new HunterAlertConfig(); c.approachInstances=1; c.approachRange=3000; var e=new ThreatEngine();
        e.update(snapshot(1000,2000,26),SELF,ORIGIN,1000,c);
        var v=e.update(snapshot(5000,1800,26),SELF,ORIGIN,5000,c);
        var a=v.events().stream().filter(x -> x.key().endsWith(":approaching")).findFirst().orElseThrow();
        assertEquals("POTENTIAL HUNTER APPROACHING",a.title()); assertTrue(a.message().contains("26 towns"));
    }
    @Test void localMotionAndStaleCoordinatesNeverProduceApproach() {
        var e=new ThreatEngine(); var c=new HunterAlertConfig(); c.approachInstances=1; c.approachRange=3000;
        e.update(snapshot(1000,2000,0),SELF,ORIGIN,1000,c);
        assertTrue(e.update(snapshot(5000,2000,0),SELF,new Position("terra",500,0),5000,c).events().isEmpty());
        assertTrue(e.update(snapshot(5000,1800,0),SELF,ORIGIN,30000,c).events().isEmpty());
    }
    @Test void rainNoticeWorksDuringMapOutageAndDoesNotRepeatEveryTick() {
        var c=new HunterAlertConfig(); c.cooldownSeconds=0; var e=new ThreatEngine();
        var s=new Snapshot(Feed.unavailable(),List.of(),Map.of(),Map.of(),0);
        assertEquals(1,e.update(s,SELF,ORIGIN,1000,c,List.of(),true).events().size());
        assertTrue(e.update(s,SELF,ORIGIN,2000,c,List.of(),true).events().isEmpty());
        assertTrue(e.update(s,SELF,ORIGIN,3000,c,List.of(),false).events().isEmpty());
        assertEquals(1,e.update(s,SELF,ORIGIN,4000,c,List.of(),true).events().size());
        e.reset(); c.rainAlerts=false;
        assertTrue(e.update(s,SELF,ORIGIN,5000,c,List.of(),true).events().isEmpty());
        c.rainAlerts=true; c.enabled=false;
        assertTrue(e.update(s,SELF,ORIGIN,6000,c,List.of(),true).events().isEmpty());
    }
    @Test void candidatesUseEntireIndexThresholdWhitelistAndSelfExclusion() {
        var c=new HunterAlertConfig(); c.minimumOutlawTowns=10; c.automatic=false;
        c.tracked.add(new HunterAlertConfig.Tracked(new Identity(null,"Friendly"),Kind.FRIENDLY,"",true));
        var s=new Snapshot(Feed.unavailable(),List.of(),Map.of(),Map.of("below",9,"exact",10,"hunter",26,"friendly",99,"self",100),1);
        var list=PlayerSuggestions.find(s,List.of(HUNTER),c,SELF,"",true);
        assertEquals(List.of("Hunter","exact"),list.stream().map(e -> e.identity().name()).toList());
        assertEquals(HUNTER.uuid(),list.getFirst().identity().uuid());
        c.minimumOutlawTowns=27; assertTrue(PlayerSuggestions.find(s,List.of(),c,SELF,"",true).isEmpty());
    }
    @Test void autocompleteIsCaseInsensitiveDeduplicatesAndIncludesSavedAndOnlineNames() {
        var c=new HunterAlertConfig(); c.tracked.add(new HunterAlertConfig.Tracked(new Identity(null,"Hunter"),Kind.KNOWN,"",true));
        var s=snapshot(1000,2000,26);
        var list=PlayerSuggestions.find(s,List.of(HUNTER,new Identity(null,"Hugo"),SELF),c,SELF,"hU",false);
        assertEquals(List.of("Hugo","Hunter"),list.stream().map(e -> e.identity().name()).toList());
        assertEquals(HUNTER.uuid(),list.get(1).identity().uuid());
        assertTrue(PlayerSuggestions.find(s,List.of(),c,SELF,"no_match",false).isEmpty());
    }
    @Test void nearbyAutomaticAffiliationWarnsWithoutClaimingObservedMovement() {
        var c=new HunterAlertConfig(); c.approachInstances=1; c.approachRange=3000; var e=new ThreatEngine();
        var towns=List.of(new Town("Nearby","Nation",new Position("terra",1000,0),"spawn"));
        for(int i=1;i<=2;i++) {
            var s=new Snapshot(new Feed(i*1000,0,true,List.of()),towns,Map.of("hunter",new Profile(HUNTER,i==1 ? "Old" : "Nearby","Nation",i*1000)),Map.of("hunter",26),i*1000);
            var v=e.update(s,SELF,ORIGIN,i*1000,c);
            if(i==1) assertTrue(v.events().isEmpty());
            else { assertEquals(1,v.events().size()); assertTrue(v.events().getFirst().message().contains("location unconfirmed")); assertTrue(v.nearby().isEmpty()); }
        }
    }
}
