package net.earthmc.hunteralert;

import net.earthmc.hunteralert.detection.MapProjection;
import net.earthmc.hunteralert.alert.AlertSounds;
import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.model.Models.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapAndSoundTest {
    @Test void mapOverlayTracksPanAndZoom() {
        var p=new Position("earth",1200,-1800);
        assertEquals(new MapProjection.Point(600,500),MapProjection.project(p,"earth",1000,-2000,1,800,600));
    }
    @Test void mapOverlayClipsAndSeparatesWorlds() {
        var p=new Position("earth",1200,-1800);
        assertEquals(new MapProjection.Point(400,200),MapProjection.project(p,"earth",1200,-1800,4,800,400));
        assertEquals(new MapProjection.Point(500,300),MapProjection.project(p,"earth",1000,-2000,.5,800,400));
        assertNull(MapProjection.project(p,"moon",1200,-1800,1,800,400));
        assertNull(MapProjection.project(p,"earth",0,0,1,800,400));
        assertNull(MapProjection.project(p,"earth",0,0,Double.NaN,800,400));
    }
    @Test void exposureAndThreatsHaveDistinctSoundsAndPreserveRoutineSound() {
        var c=new HunterAlertConfig();
        var exposed=AlertSounds.forAlert(new Alert(1,"self:map-exposed","","",3),c);
        var threat=AlertSounds.forAlert(new Alert(1,"player:Hunter:entered-critical","","",2),c);
        assertNotEquals(exposed.id(),threat.id()); assertNotEquals(exposed.pitch(),threat.pitch());
        assertEquals(c.threatSoundId,AlertSounds.forAlert(new Alert(1,"player:Hunter:affiliation-town:near","","",3),c).id());
        c.soundId="minecraft:block.note_block.harp";
        assertEquals(c.soundId,AlertSounds.forAlert(new Alert(1,"player:Other:entered-warning","","",0),c).id());
        c.exposureSoundId="bad"; c.threatSoundId=null; c.validate();
        assertEquals("minecraft:block.note_block.pling",c.exposureSoundId); assertEquals("minecraft:block.note_block.bell",c.threatSoundId);
    }
}
