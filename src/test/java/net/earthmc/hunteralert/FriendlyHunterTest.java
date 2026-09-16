package net.earthmc.hunteralert;

import net.earthmc.hunteralert.config.*;
import net.earthmc.hunteralert.detection.ThreatEngine;
import net.earthmc.hunteralert.model.Models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FriendlyHunterTest {
    private final Identity self=new Identity(null,"Self"), hunter=new Identity(null,"Hunter");
    private final Position home=new Position("earth",0,0);
    private Snapshot snapshot(long at,boolean visible,String town,boolean healthy) {
        return new Snapshot(new Feed(at,at,healthy,visible ? List.of(new MapPlayer(hunter,new Position("earth",100,0),at)) : List.of()),
            List.of(new Town("Near","Nation",home,"spawn")),Map.of("hunter",new Profile(hunter,town,"Nation",at)),Map.of("hunter",90),at);
    }
    @Test void friendlyOverridesKnownAndAutomaticEvenWithOrdinaryAlerts() {
        var c=new HunterAlertConfig(); c.ordinaryAlerts=true;
        c.tracked.add(new HunterAlertConfig.Tracked(hunter,Kind.KNOWN,"",true));
        c.tracked.add(new HunterAlertConfig.Tracked(new Identity(null,"hUnTeR"),Kind.FRIENDLY,"friend",true));
        var engine=new ThreatEngine();
        var v=engine.update(snapshot(1000,true,"Old",true),self,home,1000,c);
        assertTrue(v.nearby().isEmpty()); assertTrue(v.events().isEmpty()); assertTrue(v.markers().isEmpty());
        v=engine.update(snapshot(2000,false,"Near",true),self,home,2000,c);
        assertTrue(v.events().isEmpty()); assertTrue(v.markers().isEmpty());
    }
    @Test void trustingExistingHunterClearsStaleAndAffiliationMarkersWithoutDepartureAlert() {
        for(boolean healthy:List.of(true,false)) {
            var c=new HunterAlertConfig(); var engine=new ThreatEngine();
            engine.update(snapshot(1000,true,"Old",true),self,home,1000,c);
            assertFalse(engine.update(snapshot(2000,true,"Near",true),self,home,2000,c).markers().isEmpty());
            var friendly=c.copy(); friendly.tracked.add(new HunterAlertConfig.Tracked(hunter,Kind.FRIENDLY,"",true));
            var v=engine.update(snapshot(3000,false,"Near",healthy),self,home,3000,friendly);
            assertTrue(v.nearby().isEmpty()); assertTrue(v.markers().isEmpty()); assertTrue(v.events().isEmpty());
        }
    }
    @Test void actionBarFilterOnlyMatchesExactPlaceholder() {
        assertTrue(net.earthmc.hunteralert.detection.ActionBarFilter.isDefault("Default"));
        assertFalse(net.earthmc.hunteralert.detection.ActionBarFilter.isDefault("Default town"));
        assertFalse(net.earthmc.hunteralert.detection.ActionBarFilter.isDefault("Danger nearby"));
        assertFalse(net.earthmc.hunteralert.detection.ActionBarFilter.isDefault(null));
    }
    @Test void disabledWhitelistRestoresAutomaticDetection() {
        var c=new HunterAlertConfig(); c.tracked.add(new HunterAlertConfig.Tracked(hunter,Kind.FRIENDLY,"",false));
        var v=new ThreatEngine().update(snapshot(1000,true,"Old",true),self,home,1000,c);
        assertEquals(Kind.AUTOMATIC,v.nearby().getFirst().kind()); assertFalse(v.events().isEmpty());
    }
    @Test void friendlyIdentityAndNotePersist(@TempDir Path dir) throws Exception {
        var id=new Identity(UUID.randomUUID(),"Friend");
        try(var store=new LocalStore(dir)) {
            for(int i=0;i<100 && !store.ready();i++) Thread.sleep(20);
            assertTrue(store.ready()); var c=store.editableCopy();
            c.tracked.add(new HunterAlertConfig.Tracked(id,Kind.FRIENDLY,"Trusted ally",true)); store.save(c);
        }
        try(var store=new LocalStore(dir)) {
            for(int i=0;i<100 && !store.ready();i++) Thread.sleep(20);
            assertTrue(store.ready()); assertTrue(store.config().tracked(new Identity(id.uuid(),"NewName"),Kind.FRIENDLY));
            assertEquals("Trusted ally",store.config().tracked.getFirst().note());
        }
    }
}
