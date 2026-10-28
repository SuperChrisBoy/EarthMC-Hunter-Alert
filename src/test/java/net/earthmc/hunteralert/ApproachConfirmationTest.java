package net.earthmc.hunteralert;

import net.earthmc.hunteralert.alert.CompactAlertLog;
import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.detection.*;
import net.earthmc.hunteralert.model.Models.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static net.earthmc.hunteralert.ThreatEngineTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ApproachConfirmationTest {
    MapPlayer player(long at,double x) { return new MapPlayer(HUNTER,new Position("terra",x,0),at); }
    Snapshot snapshot(long at,double x,int outlaws) { return new Snapshot(new Feed(at,0,true,List.of(player(at,x))),List.of(),Map.of(),Map.of("hunter",outlaws),at); }
    @Test void threeDistinctConsecutiveMovementsRequiredAndNoRepeatedWarning() {
        var c=new HunterAlertConfig(); var tracker=new ApproachTracker();
        assertEquals(3,c.approachInstances);
        tracker.update(player(1000,1000),ORIGIN,1000,c);
        var one=tracker.update(player(4000,940),ORIGIN,4000,c);
        assertEquals(1,one.instances()); assertFalse(one.trigger());
        assertEquals(1,tracker.update(player(4000,940),ORIGIN,4100,c).instances());
        var two=tracker.update(player(7000,880),ORIGIN,7000,c);
        assertEquals(2,two.instances()); assertFalse(two.trigger());
        var three=tracker.update(player(10000,820),ORIGIN,10000,c);
        assertTrue(three.trigger()); assertEquals(4,three.path().size()); assertEquals(180,three.change());
        assertFalse(tracker.update(player(10000,820),ORIGIN,10100,c).trigger());
        assertFalse(tracker.update(player(11000,760),ORIGIN,11000,c).trigger());
    }
    @Test void distanceThresholdStillRequiredAfterThreeInstances() {
        var c=new HunterAlertConfig(); var tracker=new ApproachTracker();
        for(int i=0;i<=3;i++) assertFalse(tracker.update(player(1000+i*2000,1000-i*30),ORIGIN,1000+i*2000,c).trigger());
        assertTrue(tracker.update(player(9000,850),ORIGIN,9000,c).trigger());
    }
    @Test void outsideConfiguredRangeNeverCountsAndEntryStartsNewBaseline() {
        var c=new HunterAlertConfig(); c.approachRange=500; c.approachingThreshold=60;
        var t=new ApproachTracker();
        for(int i=0;i<4;i++) assertEquals(0,t.update(player(1000+i*2000,1000-i*100),ORIGIN,1000+i*2000,c).instances());
        assertEquals(0,t.update(player(9000,500),ORIGIN,9000,c).instances());
        assertEquals(1,t.update(player(11000,450),ORIGIN,11000,c).instances());
        assertEquals(0,t.update(player(13000,600),ORIGIN,13000,c).instances());
        assertEquals(0,t.update(player(15000,400),ORIGIN,15000,c).instances());
    }
    @Test void maximumRangeAlsoCapsApproachRange() {
        var c=new HunterAlertConfig(); c.maximum=500; c.approachRange=1000;
        var t=new ApproachTracker();
        t.update(player(1000,900),ORIGIN,1000,c);
        assertEquals(0,t.update(player(4000,800),ORIGIN,4000,c).instances());
    }
    @Test void stoppingTurningAwayStaleWorldAndLocalTravelResetOrCannotAdvance() {
        var c=new HunterAlertConfig(); var t=new ApproachTracker();
        t.update(player(1000,1000),ORIGIN,1000,c);
        assertEquals(1,t.update(player(4000,940),ORIGIN,4000,c).instances());
        assertEquals(0,t.update(player(7000,940),ORIGIN,7000,c).instances());
        assertEquals(0,t.update(player(10000,1000),ORIGIN,10000,c).instances());
        assertEquals(0,t.update(player(13000,1000),new Position("terra",300,0),13000,c).instances());
        assertEquals(0,t.update(player(13000,900),ORIGIN,40000,c).instances());
        assertEquals(0,t.update(new MapPlayer(HUNTER,new Position("moon",100,0),41000),ORIGIN,41000,c).instances());
    }
    @Test void configChangesAndObservationWindowRequireNewEvidence() {
        var c=new HunterAlertConfig(); var t=new ApproachTracker();
        t.update(player(1000,1000),ORIGIN,1000,c); t.update(player(4000,900),ORIGIN,4000,c);
        c.approachInstances=2;
        assertEquals(0,t.update(player(7000,800),ORIGIN,7000,c).instances());
        c.movementWindowSeconds=2;
        t.update(player(10000,700),ORIGIN,10000,c);
        assertEquals(0,t.update(player(14000,600),ORIGIN,14000,c).instances());
    }
    @Test void enginePublishesProgressAndObservedHunterPathThenOneAlert() {
        var e=new ThreatEngine(); var c=new HunterAlertConfig();
        e.update(snapshot(1000,1000,26),SELF,ORIGIN,1000,c);
        for(int i=1;i<=3;i++) {
            var v=e.update(snapshot(1000+i*3000,1000-i*60,26),SELF,ORIGIN,1000+i*3000,c);
            assertEquals(i,v.approaches().getFirst().instances()); assertTrue(v.approaches().getFirst().hunter());
            assertEquals(i==3?1:0,v.events().stream().filter(a -> a.key().endsWith(":approaching")).count());
        }
        c.tracked.add(new HunterAlertConfig.Tracked(HUNTER,Kind.FRIENDLY,"",true));
        assertTrue(e.update(snapshot(11000,700,26),SELF,ORIGIN,11000,c).approaches().isEmpty());
    }
    @Test void compactLogDeduplicatesObservationsExpiresAndClears() {
        var log=new CompactAlertLog();
        var a=new Approach(HUNTER,1,940,4000,true,List.of(ORIGIN));
        var v=new View(Exposure.unknown(),List.of(),List.of(),true,true,List.of(),List.of(a));
        log.accept(v,3); log.accept(v,3);
        assertEquals(1,log.recent(4100,60,4).size()); assertTrue(log.recent(4100,60,4).getFirst().text().contains("1/3"));
        assertTrue(log.recent(65000,60,4).isEmpty());
        log.clear(); log.accept(v,3); assertEquals(1,log.recent(4100,60,4).size());
    }
    @Test void compactLogFitsSmallWindowsAndHonorsRowsAndScale() {
        for(int w:List.of(320,427,854,1920)) for(int h:List.of(180,240,480,1080)) for(double scale:List.of(.5,1.0,2.0,3.0)) {
            var layout=CompactAlertLog.layout(w,h,scale,8);
            assertTrue(layout.width()*scale<=w*.48);
            // At extreme scale the single-line header is the minimum readable panel.
            assertTrue((layout.rows()+1)*12*scale+12*scale<=Math.max(h*.25,24*scale));
            assertTrue(layout.rows()<=8);
        }
        assertTrue(new HunterAlertConfig().logView);
    }
    @Test void existingSettingsAcquireSafeDefaultsWithoutOverwritingCustomThreshold() {
        var c=new com.google.gson.Gson().fromJson("{\"approachingThreshold\":60}",HunterAlertConfig.class);
        assertEquals(3,c.approachInstances); assertTrue(c.logView); assertTrue(c.pathTrace); assertEquals(60,c.approachingThreshold);
        c.approachInstances=0; c.approachRange=Double.NaN; c.logRows=100; c.logSeconds=-1; c.validate();
        assertEquals(1,c.approachInstances); assertEquals(1500,c.approachRange); assertEquals(8,c.logRows); assertEquals(5,c.logSeconds);
    }
    @Test void traceSegmentsClipAtViewportAndRejectOtherWorldsOrInvalidInput() {
        var s=TraceGeometry.project(new Position("terra",-1000,0),new Position("terra",1000,0),"terra",0,0,1,100,100);
        assertEquals(0,s.x1()); assertEquals(99,s.x2(),.00001); assertEquals(50,s.y1());
        assertNull(TraceGeometry.project(ORIGIN,new Position("moon",1,0),"terra",0,0,1,100,100));
        assertNull(TraceGeometry.clip(Double.NaN,0,1,1,100,100));
        assertNull(TraceGeometry.clip(-100,-100,-1,-1,100,100));
    }
}
