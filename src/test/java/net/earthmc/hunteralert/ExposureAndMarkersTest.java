package net.earthmc.hunteralert;

import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.detection.*;
import net.earthmc.hunteralert.model.Models.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExposureAndMarkersTest {
    private static final Identity SELF=new Identity(null,"Self"),HUNTER=new Identity(null,"Hunter");
    private static final Position HOME=new Position("earth",0,0);
    private Snapshot snapshot(long at,List<MapPlayer> players,List<Town> towns,Map<String,Profile> profiles,Map<String,Integer> outlaws) { return new Snapshot(new Feed(at,at,true,players),towns,profiles,outlaws,at); }
    @Test void percentageFillsDrainsPausesAndResets() {
        var tracker=new ExposureTracker(); tracker.update(Visibility.EXPOSED,1000,2,4);
        assertEquals(50,tracker.update(Visibility.EXPOSED,2000,2,4).percent(),.001);
        assertEquals(100,tracker.update(Visibility.EXPOSED,3000,2,4).percent(),.001);
        assertEquals(100,tracker.update(Visibility.EXPOSED,4000,2,4).percent(),.001);
        tracker.update(Visibility.HIDDEN,4000,2,4);
        assertEquals(75,tracker.update(Visibility.HIDDEN,5000,2,4).percent(),.001);
        assertEquals(75,tracker.update(Visibility.UNKNOWN,6000,2,4).percent(),.001);
        assertEquals(75,tracker.update(Visibility.UNKNOWN,90_000,2,4).percent(),.001);
        assertEquals(75,tracker.update(Visibility.HIDDEN,91_000,2,4).percent(),.001);
        for(int i=92;i<100;i++) tracker.update(Visibility.HIDDEN,i*1000L,2,4);
        assertEquals(0,tracker.state().percent()); tracker.reset(); assertEquals(Exposure.unknown(),tracker.state());
    }
    @Test void highOutlawCountLeadsAndSeverityIsColorCoded() {
        var c=new HunterAlertConfig();var e=new ThreatEngine();
        var low=new MapPlayer(new Identity(null,"Low"),new Position("earth",20,0),1000);
        var high=new MapPlayer(HUNTER,new Position("earth",1200,0),1000);
        var result=e.update(snapshot(1000,List.of(low,high),List.of(),Map.of(),Map.of("low",10,"hunter",80)),SELF,HOME,1000,c);
        assertEquals("Hunter",result.nearby().getFirst().player().identity().name());
        assertNotEquals(ThreatPresentation.color(result.nearby().getFirst()),ThreatPresentation.color(result.nearby().get(1)));
    }
    @Test void disappearanceBecomesLastSeenAndExpiresWithoutInventingMotion() {
        var c=new HunterAlertConfig(); c.expireSeconds=30; var e=new ThreatEngine();
        var p=new MapPlayer(HUNTER,new Position("earth",100,0),1000);
        var first=e.update(snapshot(1000,List.of(p),List.of(),Map.of(),Map.of("hunter",20)),SELF,HOME,1000,c);
        assertEquals(MarkerType.LIVE,first.markers().getFirst().type());
        var hidden=e.update(snapshot(2000,List.of(),List.of(),Map.of(),Map.of("hunter",20)),SELF,HOME,2000,c);
        assertTrue(hidden.nearby().isEmpty()); assertEquals(MarkerType.LAST_SEEN,hidden.markers().getFirst().type()); assertEquals(p.position(),hidden.markers().getFirst().position());
        assertTrue(e.update(snapshot(32_000,List.of(),List.of(),Map.of(),Map.of("hunter",20)),SELF,HOME,32_000,c).markers().isEmpty());
        e.reset(); assertTrue(e.update(snapshot(33_000,List.of(),List.of(),Map.of(),Map.of()),SELF,HOME,33_000,c).markers().isEmpty());
    }
    @Test void hiddenHunterAffiliationNearHomeAlertsAndMarksTownNotPlayer() {
        var c=new HunterAlertConfig(); c.affiliationRadius=500; var e=new ThreatEngine();
        Position away=new Position("earth",50_000,0);
        var towns=List.of(new Town("Home","Ours",HOME,"spawn"),new Town("Danger","Theirs",new Position("earth",200,0),"spawn"));
        var first=Map.of("self",new Profile(SELF,"Home","Ours",1000),"hunter",new Profile(HUNTER,"Old","OldNation",1000));
        e.update(snapshot(1000,List.of(),towns,first,Map.of("hunter",40)),SELF,away,1000,c);
        var next=Map.of("self",new Profile(SELF,"Home","Ours",2000),"hunter",new Profile(HUNTER,"Danger","Theirs",2000));
        var result=e.update(snapshot(2000,List.of(),towns,next,Map.of("hunter",40)),SELF,away,2000,c);
        assertEquals(2,result.events().size()); assertTrue(result.nearby().isEmpty());
        assertEquals(2,result.markers().size()); assertTrue(result.markers().stream().allMatch(m -> m.type()==MarkerType.AFFILIATION && m.position().equals(towns.get(1).anchor())));
        assertTrue(result.events().stream().allMatch(a -> a.message().contains("location unconfirmed")));
        assertTrue(e.update(snapshot(400_000,List.of(),towns,next,Map.of("hunter",40)),SELF,away,400_000,c).markers().isEmpty());
    }
    @Test void distantOrCrossWorldAffiliationDoesNotGenerateClue() {
        for(Position anchor:List.of(new Position("earth",100_000,0),new Position("moon",20,0))) {
            var e=new ThreatEngine(); var c=new HunterAlertConfig();
            var towns=List.of(new Town("Distant","Nation",anchor,"spawn"));
            e.update(snapshot(1000,List.of(),towns,Map.of("hunter",new Profile(HUNTER,"Old","",1000)),Map.of("hunter",20)),SELF,HOME,1000,c);
            var view=e.update(snapshot(2000,List.of(),towns,Map.of("hunter",new Profile(HUNTER,"Distant","Nation",2000)),Map.of("hunter",20)),SELF,HOME,2000,c);
            assertEquals(2,view.events().size()); assertTrue(view.events().stream().allMatch(a -> a.message().contains("joined"))); assertTrue(view.markers().isEmpty());
        }
    }
    @Test void localMovementIntoHunterRangeAlertsBetweenFeedRefreshes() {
        var e=new ThreatEngine(); var c=new HunterAlertConfig();
        var p=new MapPlayer(HUNTER,new Position("earth",100,0),1000);
        var snapshot=snapshot(1000,List.of(p),List.of(),Map.of(),Map.of("hunter",40));
        assertTrue(e.update(snapshot,SELF,new Position("earth",20000,0),1000,c).events().isEmpty());
        var near=e.update(snapshot,SELF,HOME,1500,c);
        assertTrue(near.events().stream().anyMatch(a -> a.title().equals("HUNTER DETECTED")));
        assertEquals(MarkerType.LIVE,near.markers().getFirst().type());
        assertTrue(e.update(snapshot,SELF,HOME,1600,c).events().isEmpty());
    }
    @Test void onlineHunterExistingTownWarnsWhenUserEntersRange() {
        var e=new ThreatEngine(); var c=new HunterAlertConfig(); c.affiliationRadius=500;
        var towns=List.of(new Town("Near","",new Position("earth",200,0),"spawn"));
        var profiles=Map.of("hunter",new Profile(HUNTER,"Near","",1000,Presence.ONLINE));
        var snapshot=snapshot(1000,List.of(),towns,profiles,Map.of("hunter",40));
        assertTrue(e.update(snapshot,SELF,new Position("earth",10000,0),1000,c).events().isEmpty());
        var near=e.update(snapshot,SELF,HOME,2000,c);
        assertEquals(1,near.events().size());
        assertTrue(near.events().getFirst().message().contains("Near"));
        assertEquals(MarkerType.AFFILIATION,near.markers().getFirst().type());
        assertTrue(e.update(snapshot,SELF,HOME,3000,c).events().isEmpty());
    }
    @Test void projectedMapBearingRespectsYawPitchAndBehindCamera() {
        var ahead=MarkerProjection.project(0,100,0,0,70,800,450); assertNotNull(ahead); assertEquals(400,ahead.x(),.01); assertEquals(225,ahead.y(),.01);
        assertNull(MarkerProjection.project(0,-100,0,0,70,800,450));
        assertNotNull(MarkerProjection.project(100,0,-90,0,70,800,450));
        assertTrue(MarkerProjection.project(0,100,0,10,70,800,450).y()<225);
    }
}
