package net.earthmc.hunteralert;
import net.earthmc.hunteralert.detection.WrappedHud;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WrappedHudTest {
 @Test void longAffiliationIsCompleteAcrossWidths() {
  String text="13s Affiliation: hungrybalong6 changed nation to NearbyNation. Their town is near you; possible incoming travel.";
  for(int width:new int[]{12,20,32,50,100}) {
   var lines=WrappedHud.wrap(text,width,String::length);
   assertEquals(text.replace(" ",""),String.join("",lines).replace(" ",""));
   if(width>=20) assertEquals(text,String.join(" ",lines));
   assertTrue(lines.stream().allMatch(s->s.length()<=width));
  }
 }
 @Test void longUnbrokenNamesAndUnicodeAreNotDiscarded() {
  String text="abcdefghijklmnopqrstuvwxyz😀😀abcdef";
  var lines=WrappedHud.wrap(text,5,String::length);
  assertEquals(text,String.join("",lines));
  assertTrue(lines.stream().allMatch(s->s.length()<=5));
  assertTrue(lines.stream().noneMatch(s->Character.isHighSurrogate(s.charAt(s.length()-1))));
 }
 @Test void explicitParagraphsRemainSeparate() {
  assertEquals(List.of("first","","last"),WrappedHud.wrap("first\n\nlast",40,String::length));
 }
 @Test void pinnedHunterRemainsVisibleOnEveryLogPage() {
  var fixed=List.of("Hunter 800b E","LIVE X 800 Z 42");
  var log=List.of("one","two","three","four","five");
  var seen=new ArrayList<String>();
  for(int i=0;i<3;i++) {
   var page=WrappedHud.page(fixed,log,4,i*8000L);
   assertEquals(3,page.count()); assertEquals(i+1,page.number());
   assertEquals(fixed,page.lines().subList(0,2));
   seen.addAll(page.lines().subList(2,page.lines().size()));
  }
  assertEquals(log,seen);
 }
 @Test void tinyCapacityStillMakesEveryLineAvailable() {
  var fixed=List.of("hunter","coordinates"); var log=List.of("alert","continuation");
  var seen=new ArrayList<String>();
  for(int i=0;i<4;i++) seen.addAll(WrappedHud.page(fixed,log,1,i*8000L).lines());
  assertEquals(List.of("hunter","coordinates","alert","continuation"),seen);
 }
 @Test void emptyContentAndPageWrapAreSafe() {
  assertEquals(List.of(),WrappedHud.page(List.of(),List.of(),0,0).lines());
  assertEquals(1,WrappedHud.page(List.of(),List.of("one","two"),1,16000).number());
 }
}
