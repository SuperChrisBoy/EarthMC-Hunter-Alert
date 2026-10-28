package net.earthmc.hunteralert.detection;

import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.model.Models.*;
import java.util.*;

/** Counts independent, consecutive map movements inside the configured approach range. */
public final class ApproachTracker {
    private record Limits(double range,double minimum,double threshold,int instances,int window,int stale) {}
    private static final class Track {
        final Deque<MapPlayer> samples=new ArrayDeque<>();
        boolean alerted;
    }
    public record Result(int instances,double change,long millis,List<Position> path,boolean trigger) {}
    private final Map<String,Track> tracks=new HashMap<>();
    private Limits limits;
    public Result update(MapPlayer player,Position local,long now,HunterAlertConfig c) {
        var next=new Limits(Math.min(c.approachRange,c.maximum),c.minimumMovement,c.approachingThreshold,c.approachInstances,c.movementWindowSeconds,c.staleSeconds);
        if(!next.equals(limits)) { clear(); limits=next; }
        String id=player.identity().key();
        var distance=local.distance(player.position());
        if(!c.movement || distance.isEmpty() || distance.getAsDouble()>next.range || player.updatedAt()>now || now-player.updatedAt()>c.staleSeconds*1000L) {
            tracks.remove(id); return empty();
        }
        Track t=tracks.computeIfAbsent(id,k -> new Track());
        MapPlayer last=t.samples.peekLast();
        if(last!=null && player.updatedAt()<last.updatedAt()) { tracks.remove(id); return empty(); }
        boolean fresh=last==null || player.updatedAt()>last.updatedAt();
        if(fresh) {
            boolean continues=last!=null && local.distance(last.position()).isPresent()
                && local.distance(last.position()).getAsDouble()<=next.range
                && player.updatedAt()-last.updatedAt()<=c.staleSeconds*1000L
                && local.distance(last.position()).getAsDouble()-distance.getAsDouble()>=c.minimumMovement;
            if(!continues) { t.samples.clear(); t.alerted=false; }
            t.samples.addLast(player);
        }
        while(t.samples.size()>1 && (now-t.samples.getFirst().updatedAt()>c.movementWindowSeconds*1000L || t.samples.size()>64)) t.samples.removeFirst();
        MapPlayer first=t.samples.getFirst();
        int count=t.samples.size()-1;
        double change=local.distance(first.position()).orElse(distance.getAsDouble())-distance.getAsDouble();
        long millis=player.updatedAt()-first.updatedAt();
        boolean ready=count>=c.approachInstances && change>=c.approachingThreshold && millis>=2000;
        boolean trigger=fresh && ready && !t.alerted;
        if(fresh) t.alerted=ready;
        return new Result(count,change,millis,t.samples.stream().map(MapPlayer::position).toList(),trigger);
    }
    private static Result empty() { return new Result(0,0,0,List.of(),false); }
    public void retain(Set<String> ids) { tracks.keySet().retainAll(ids); }
    public void clear() { tracks.clear(); }
}
