package net.earthmc.hunteralert.detection;

import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.model.Models.*;
import java.util.*;
import java.util.function.ToIntFunction;

/** Persistent awareness rows are derived from current evidence, not expiring alert messages. */
public final class AwarenessHud {
    public record Row(String identity,String location,boolean stale) {}
    public static List<Row> rows(View view,HunterAlertConfig c,long now) {
        List<Row> rows=new ArrayList<>(); Set<String> seen=new HashSet<>();
        view.nearby().stream().filter(a -> a.kind().priority>=2 && a.radius()!=Radius.OUTSIDE && a.distance()<=c.maximum)
            .filter(a -> !c.tracked(a.player().identity(),Kind.FRIENDLY))
            .filter(a -> now>=a.lastSeenAt() && now-a.lastSeenAt()<=c.expireSeconds*1000L)
            .sorted(ThreatPresentation.ORDER).forEach(a -> {
                boolean stale=a.stale() || !view.healthy() || now-a.lastSeenAt()>c.staleSeconds*1000L;
                seen.add(a.player().identity().key());
                rows.add(new Row(a.player().identity().name()+" | "+(stale?"last ":"")+Math.round(a.distance())+"b "+a.direction(),
                    coordinates(a.player().position(),stale),stale));
            });
        for(var marker:view.markers()) {
            if(marker.type()!=MarkerType.LAST_SEEN || now>=marker.expiresAt() || seen.contains(marker.identity().key()) || c.tracked(marker.identity(),Kind.FRIENDLY)) continue;
            seen.add(marker.identity().key());
            rows.add(new Row(marker.identity().name()+" | location unknown",coordinates(marker.position(),true),true));
        }
        return List.copyOf(rows);
    }
    private static String coordinates(Position p,boolean stale) {
        return (stale?"LAST ":"LIVE ")+"X "+Math.round(p.x())+" Z "+Math.round(p.z());
    }
    public static String fit(String text,int width,ToIntFunction<String> measure) {
        if(measure.applyAsInt(text)<=width) return text;
        if(measure.applyAsInt("...")>width) return "";
        String prefix=text;
        while(!prefix.isEmpty() && measure.applyAsInt(prefix+"...")>width)
            prefix=prefix.substring(0,prefix.offsetByCodePoints(prefix.length(),-1));
        int space=prefix.lastIndexOf(' ');
        if(space>prefix.length()/2) prefix=prefix.substring(0,space);
        return prefix.stripTrailing()+"...";
    }
    private AwarenessHud() {}
}
