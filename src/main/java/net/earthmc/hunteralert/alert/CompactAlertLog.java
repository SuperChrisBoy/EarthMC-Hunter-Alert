package net.earthmc.hunteralert.alert;

import net.earthmc.hunteralert.model.Models.*;
import java.util.*;

/** Client-thread session log; approach observations are silent and never persisted as alerts. */
public final class CompactAlertLog {
    public record Entry(long at,String text,int priority) {}
    public record Layout(int width,int rows) {}
    private final Deque<Entry> entries=new ArrayDeque<>();
    private final Map<String,Long> observed=new HashMap<>();
    public void accept(View view,int required) {
        Set<String> active=new HashSet<>();
        for(var a:view.approaches()) {
            String id=a.identity().key(); active.add(id);
            long old=observed.getOrDefault(id,0L); observed.put(id,a.observedAt());
            if(a.observedAt()<=old || a.instances()<1) continue;
            if(view.events().stream().anyMatch(e -> e.key().equals("player:"+id+":approaching"))) continue;
            append(new Entry(a.observedAt(),a.identity().name()+" "+a.instances()+"/"+required+" -> "+Math.round(a.distance())+"b",0));
        }
        observed.keySet().retainAll(active);
        for(var a:view.events().reversed()) {
            if(a.key().startsWith("self:map-")) continue;
            String title=switch(a.title()) {
                case "POTENTIAL HUNTER APPROACHING" -> "Potential hunter";
                case "HUNTER APPROACHING" -> "Hunter approach";
                case "PLAYER APPROACHING" -> "Approach";
                case "HUNTER DETECTED" -> "Hunter nearby";
                case "AFFILIATION WARNING", "AFFILIATION CHANGE" -> "Town";
                case "LEFT PUBLIC MAP" -> "Left map";
                case "RAIN / RIPTIDE RISK" -> "Rain risk";
                default -> a.title();
            };
            var approach=view.approaches().stream().filter(p -> a.key().equals("player:"+p.identity().key()+":approaching")).findFirst();
            String detail=approach.map(p -> p.identity().name()+" "+p.instances()+"/"+required+" "+Math.round(p.distance())+"b").orElse(a.message());
            String tag=a.title().equals("POTENTIAL HUNTER APPROACHING") ? "[H?]" : a.title().equals("HUNTER APPROACHING") ? "[H]" : "[P]";
            String message=switch(a.key()) {
                case "self:map-exposed" -> "MAP EXPOSED";
                case "self:map-hidden" -> "MAP HIDDEN";
                case "weather:rain-risk" -> "Rain: Riptide travel risk";
                default -> approach.isPresent() ? tag+" "+detail : (a.title().startsWith("AFFILIATION") ? detail : title+": "+detail);
            };
            append(new Entry(a.at(),message,a.priority()));
        }
    }
    private void append(Entry entry) { entries.addFirst(entry); while(entries.size()>64) entries.removeLast(); }
    public List<Entry> recent(long now,int seconds,int limit) {
        entries.removeIf(e -> now-e.at()>seconds*1000L || e.at()>now);
        return entries.stream().limit(limit).toList();
    }
    public static Layout layout(int width,int height,double scale,int requestedRows) {
        int w=Math.max(1,(int)(Math.min(300,width*.48)/scale));
        int rows=Math.max(0,Math.min(requestedRows,(int)((height*.25/scale-12)/12)-1));
        return new Layout(w,rows);
    }
    public void clear() { entries.clear(); observed.clear(); }
}
