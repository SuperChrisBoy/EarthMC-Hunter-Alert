package net.earthmc.hunteralert;
import net.earthmc.hunteralert.alert.CompactAlertLog;
import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.detection.AwarenessHud;
import net.earthmc.hunteralert.model.Models.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AwarenessHudTest {
 Assessment hunter(long at,double x,Radius radius) {
  return new Assessment(new MapPlayer(new Identity(null,"Hunter"),new Position("terra",x,42),at),Kind.KNOWN,true,false,false,20,x,"E",radius,Trend.STATIONARY,0,0,false,at);
 }
 View view(Assessment a) { return new View(Exposure.unknown(),List.of(a),List.of(),true,true); }
 @Test void liveRowsPersistWithoutEventsAndFollowCoordinates() {
  var c=new HunterAlertConfig();
  var first=AwarenessHud.rows(view(hunter(1000,800,Radius.WARNING)),c,1000);
  assertEquals("Hunter | 800b E",first.getFirst().identity());
  var later=AwarenessHud.rows(view(hunter(200000,700,Radius.WARNING)),c,200000);
  assertEquals("Hunter | 700b E",later.getFirst().identity());
  assertEquals("LIVE X 700 Z 42",later.getFirst().location());
 }
 @Test void leavingRangeOrClearingViewRemovesPinnedRows() {
  var c=new HunterAlertConfig();
  assertTrue(AwarenessHud.rows(view(hunter(1000,6000,Radius.OUTSIDE)),c,1000).isEmpty());
  c.maximum=500;
  assertTrue(AwarenessHud.rows(view(hunter(1000,800,Radius.WARNING)),c,1000).isEmpty());
  assertTrue(AwarenessHud.rows(View.empty(),c,1000).isEmpty());
 }
 @Test void staleCoordinatesAreExplicitAndEventuallyExpire() {
  var c=new HunterAlertConfig(); var v=view(hunter(1000,800,Radius.WARNING));
  var row=AwarenessHud.rows(v,c,20000).getFirst();
  assertTrue(row.stale()); assertEquals("LAST X 800 Z 42",row.location());
  assertTrue(AwarenessHud.rows(v,c,200000).isEmpty());
 }
 @Test void clippedTextHasVisibleEllipsisAndFits() {
  String s=AwarenessHud.fit("Your position is now in the public map feed",40,String::length);
  assertTrue(s.endsWith("...")); assertTrue(s.length()<=40); assertFalse(s.endsWith(" f"));
  assertEquals("short",AwarenessHud.fit("short",10,String::length));
  assertEquals("",AwarenessHud.fit("long",2,String::length));
 }
 @Test void mapExposureDoesNotDuplicatePersistentStatus() {
  var log=new CompactAlertLog();
  log.accept(new View(Exposure.unknown(),List.of(),List.of(new Alert(1000,"self:map-exposed","MAP EXPOSED","Your position is now in the public map feed",1)),true,true),3);
  assertTrue(log.recent(1000,60,4).isEmpty());
 }
}
