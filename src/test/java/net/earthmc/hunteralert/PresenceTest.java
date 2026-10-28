package net.earthmc.hunteralert;

import com.google.gson.JsonParser;
import net.earthmc.hunteralert.data.DirectEarthMcDataProvider;
import net.earthmc.hunteralert.detection.ThreatEngine;
import net.earthmc.hunteralert.model.Models.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static net.earthmc.hunteralert.ThreatEngineTest.*;

class PresenceTest {
    private final ThreatEngineTest fixture=new ThreatEngineTest();
    net.earthmc.hunteralert.config.HunterAlertConfig config() { return fixture.config(); }
    MapPlayer player(double x,String world,long at) { return fixture.player(x,world,at); }
    Snapshot snapshot(long at,MapPlayer... players) { return fixture.snapshot(at,players); }
    Snapshot status(long at, Presence presence, long observed, boolean visible) {
        return new Snapshot(snapshot(at,visible ? new MapPlayer[]{player(100,"terra",at)} : new MapPlayer[]{}).feed(),
            List.of(),Map.of("hunter",new Profile(HUNTER,"Town","Nation",observed,presence)),Map.of(),at);
    }
    @Test void explicitOfflineSuppressesEntryAndClearsPriorThreatWithoutDeparture() {
        var e=new ThreatEngine(); var c=config();
        assertFalse(e.update(snapshot(1000,player(100,"terra",1000)),SELF,ORIGIN,1000,c).markers().isEmpty());
        var offline=e.update(status(2000,Presence.OFFLINE,2000,true),SELF,ORIGIN,2000,c);
        assertTrue(offline.events().isEmpty()); assertTrue(offline.nearby().isEmpty()); assertTrue(offline.markers().isEmpty());
        assertTrue(e.update(status(3000,Presence.OFFLINE,3000,false),SELF,ORIGIN,3000,c).events().isEmpty());
        assertTrue(new ThreatEngine().update(status(1000,Presence.OFFLINE,1000,true),SELF,ORIGIN,1000,c).events().isEmpty());
    }
    @Test void serverPresenceOverridesOfflineApiAndUnknownOptOutStillAlerts() {
        var s=status(1000,Presence.OFFLINE,1000,true);
        assertFalse(new ThreatEngine().update(s,SELF,ORIGIN,1000,config(),List.of(HUNTER)).events().isEmpty());
        assertFalse(new ThreatEngine().update(status(1000,Presence.UNKNOWN,1000,true),SELF,ORIGIN,1000,config()).events().isEmpty());
        assertFalse(new ThreatEngine().update(snapshot(1000,player(100,"terra",1000)),SELF,ORIGIN,1000,config()).events().isEmpty());
    }
    @Test void staleAndFutureOfflineEvidenceCannotSuppressFreshMapPlayer() {
        for(long observed:List.of(1000L,100001L))
            assertFalse(new ThreatEngine().update(status(100000,Presence.OFFLINE,observed,true),SELF,ORIGIN,100000,config()).events().isEmpty());
    }
    @Test void disappearanceIsInformationalAndExplicitlyUncertain() {
        var e=new ThreatEngine(); var c=config();
        e.update(snapshot(1000,player(100,"terra",1000)),SELF,ORIGIN,1000,c);
        var alert=e.update(snapshot(2000),SELF,ORIGIN,2000,c).events().getFirst();
        assertEquals(1,alert.priority()); assertTrue(alert.message().contains("last 100b"));
        assertTrue(alert.message().contains("location unknown"));
    }
    @Test void offlineEvidenceAlsoClearsThreatsDuringFeedOutage() {
        var e=new ThreatEngine(); var c=config();
        e.update(snapshot(1000,player(100,"terra",1000)),SELF,ORIGIN,1000,c);
        var s=status(2000,Presence.OFFLINE,2000,true);
        var v=e.update(new Snapshot(Feed.unavailable(),s.towns(),s.profiles(),s.outlawCounts(),2000),SELF,ORIGIN,2000,c);
        assertTrue(v.nearby().isEmpty()); assertTrue(v.markers().isEmpty()); assertTrue(v.events().isEmpty());
    }
    @Test void missingNullAndMalformedApiStatusAreUnknown() {
        for(String json:List.of("{}","{\"status\":null}","{\"status\":{}}","{\"status\":{\"isOnline\":null}}","{\"status\":{\"isOnline\":\"false\"}}"))
            assertEquals(Presence.UNKNOWN,DirectEarthMcDataProvider.parsePresence(JsonParser.parseString(json).getAsJsonObject()));
        assertEquals(Presence.OFFLINE,DirectEarthMcDataProvider.parsePresence(JsonParser.parseString("{\"status\":{\"isOnline\":false}}").getAsJsonObject()));
        assertEquals(Presence.ONLINE,DirectEarthMcDataProvider.parsePresence(JsonParser.parseString("{\"status\":{\"isOnline\":true}}").getAsJsonObject()));
    }
    @Test void offlineAffiliationChangesUpdateBaselineWithoutWarningOrMarker() {
        var e=new ThreatEngine(); var c=config(); c.knownTownChanges=true;
        var town=new Town("NewTown","Nation",ORIGIN,"spawn");
        for(int i=1;i<=3;i++) {
            Presence presence=i==2 ? Presence.OFFLINE : Presence.ONLINE;
            var p=new Profile(HUNTER,i==1 ? "OldTown" : "NewTown","Nation",i*1000,presence);
            var s=new Snapshot(new Feed(i*1000,0,true,List.of()),List.of(town),Map.of("hunter",p),Map.of(),i*1000);
            var v=e.update(s,SELF,ORIGIN,i*1000,c);
            if(i==2) { assertTrue(v.events().isEmpty()); assertTrue(v.markers().isEmpty()); }
            else { assertFalse(v.events().isEmpty()); assertFalse(v.markers().isEmpty());
                assertTrue(v.events().stream().noneMatch(a -> a.message().contains("joined"))); }
        }
    }
}
