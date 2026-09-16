package net.earthmc.hunteralert.detection;

import net.earthmc.hunteralert.alert.AlertManager;
import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.model.Models;
import net.earthmc.hunteralert.model.Models.*;
import java.util.*;

/** Single-worker-owned engine; consumes immutable normalized snapshots, never performs I/O. */
public final class ThreatEngine {
    private final ExposureTracker exposure = new ExposureTracker();
    private final MovementTracker movement = new MovementTracker();
    private final AlertManager alerts = new AlertManager();
    private final Map<String, Assessment> previous = new HashMap<>();
    private final Map<String, Profile> affiliations = new HashMap<>();
    private final Map<String,ThreatMarker> markers=new HashMap<>();
    private String localWorld = "";
    private long lastFeed;
    private boolean suspended;
    private HunterAlertConfig lastConfig;

    public View update(Snapshot snapshot, Identity self, Position local, long now, HunterAlertConfig config) {
        List<Alert> events = new ArrayList<>();
        Feed feed = snapshot.feed();
        boolean healthy = config.enabled && feed.healthy() && feed.receivedAt() > 0 && now >= feed.receivedAt() && now-feed.receivedAt() <= config.staleSeconds*1000L;
        if (!healthy) {
            exposure.update(Visibility.UNKNOWN,now,config.exposureRiseSeconds,config.exposureFallSeconds);
            movement.clear();
            // Keep last positions for display, but never infer departures from a failed feed.
            var stale = previous.values().stream().filter(a -> a.radius() != Radius.OUTSIDE && now-a.lastSeenAt() <= config.expireSeconds*1000L)
                .map(a -> new Assessment(a.player(),a.kind(),a.knownHunter(),a.automaticHunter(),a.watchedPlayer(),a.outlawCount(),a.distance(),a.direction(),a.radius(),Trend.UNKNOWN,0,0,true,a.lastSeenAt())).toList();
            previous.entrySet().removeIf(entry -> now-entry.getValue().lastSeenAt()>config.expireSeconds*1000L);
            suspended=true; lastFeed = 0;
            if(config.enabled && local!=null) updateAffiliations(snapshot,self,local,now,config,events);
            return new View(exposure.state(),stale.stream().sorted(ThreatPresentation.ORDER).toList(),events,false,local != null,markerView(Set.of(),local,now,config));
        }
        if(suspended) { previous.clear(); suspended=false; }
        Optional<MapPlayer> me = feed.players().stream().filter(p -> self.matches(p.identity())).findFirst();
        boolean selfStale = me.isPresent() && now-me.get().updatedAt() > config.staleSeconds*1000L;
        Visibility old = exposure.state().state();
        Visibility state = selfStale ? Visibility.UNKNOWN : me.isPresent() ? Visibility.EXPOSED : Visibility.HIDDEN;
        exposure.update(state,now,config.exposureRiseSeconds,config.exposureFallSeconds);
        if (old == Visibility.HIDDEN && state == Visibility.EXPOSED && config.exposedAlert) emit(events,now,"self:map-exposed","MAP EXPOSED","Your position is now in the public map feed.",3,config);
        if (old == Visibility.EXPOSED && state == Visibility.HIDDEN && config.hiddenAlert) emit(events,now,"self:map-hidden","MAP HIDDEN","Your name is absent from the healthy public map feed.",1,config);
        // Public self coordinates are authoritative when fresh; the platform fallback is local-only.
        if (me.isPresent() && !selfStale) local = me.get().position();
        if (local == null || local.world().isBlank()) {
            previous.clear(); movement.clear();
            return new View(exposure.state(),List.of(),events,true,false,List.of());
        }
        if (!local.world().equals(localWorld)) { previous.clear(); movement.clear(); localWorld = local.world(); lastFeed = 0; }
        boolean newFeed = feed.receivedAt() > lastFeed || config != lastConfig;
        lastConfig=config;
        Map<String,Assessment> current = new HashMap<>();
        Profile selfProfile = snapshot.profiles().get(Models.key(self.name()));
        Set<String> visible = new HashSet<>();
        for (MapPlayer player : feed.players()) {
            if (self.matches(player.identity())) continue;
            String id = player.identity().key(); visible.add(id);
            OptionalDouble distance = local.distance(player.position());
            if (distance.isEmpty() || now-player.updatedAt() > config.expireSeconds*1000L) continue;
            Profile profile = snapshot.profiles().get(Models.key(player.identity().name()));
            boolean known = config.tracked(player.identity(),Kind.KNOWN);
            boolean watched = config.tracked(player.identity(),Kind.WATCHED);
            if (!known && ignored(selfProfile,profile,config)) continue;
            int outlaws = snapshot.outlawCounts().getOrDefault(Models.key(player.identity().name()),0);
            boolean automatic = config.automatic && outlaws >= config.minimumOutlawTowns;
            Kind kind = classify(known,automatic,watched);
            Radius radius = config.radius(distance.getAsDouble());
            var motion = movement.update(player,local,now,config);
            boolean stale = now-player.updatedAt() > config.staleSeconds*1000L;
            Assessment assessment = new Assessment(player,kind,known,automatic,watched,outlaws,distance.getAsDouble(),local.direction(player.position()),radius,motion.trend(),motion.change(),motion.millis(),stale,player.updatedAt());
            current.put(id,assessment);
            Assessment before = previous.get(id);
            if (!newFeed || stale || !shouldAlert(kind,config)) continue;
            String prefix = "player:"+id+":";
            if (radius != Radius.OUTSIDE && (before == null || before.radius() != radius || before.kind() != kind)) {
                emit(events,now,prefix+"entered-"+radius.name().toLowerCase(Locale.ROOT),kind.priority >= 2 ? "HUNTER DETECTED" : "NEARBY PLAYER",
                    player.identity().name()+" entered "+radius.name().toLowerCase(Locale.ROOT)+": "+Math.round(assessment.distance())+" blocks "+assessment.direction()+"; outlawed by "+outlaws+" towns",kind.priority,config);
            }
            if (radius == Radius.OUTSIDE && before != null && before.radius() != Radius.OUTSIDE && config.leaveAlerts)
                emit(events,now,prefix+"left-area","LEFT AREA",player.identity().name()+" left the configured detection area.",kind.priority,config);
            if (radius != Radius.OUTSIDE && motion.trend() == Trend.APPROACHING && -motion.change() >= config.approachingThreshold && (before == null || before.trend() != Trend.APPROACHING || -before.distanceChange() < config.approachingThreshold))
                emit(events,now,prefix+"approaching","APPROACHING",player.identity().name()+": "+Math.round(motion.change())+" blocks / "+motion.millis()/1000+" sec (short map trend)",kind.priority,config);
        }
        if (newFeed && config.leaveAlerts) for (var entry : previous.entrySet()) {
            Assessment before = entry.getValue();
            if (!visible.contains(entry.getKey()) && before.radius() != Radius.OUTSIDE && !before.stale() && shouldAlert(before.kind(),config))
                emit(events,now,"player:"+entry.getKey()+":disappeared","LEFT PUBLIC MAP",before.player().identity().name()+" disappeared; last map distance "+Math.round(before.distance())+" blocks. Current location unknown.",before.kind().priority,config);
        }
        movement.retain(current.keySet());
        previous.clear(); previous.putAll(current); lastFeed = Math.max(lastFeed,feed.receivedAt());
        for(String id:visible) if(!current.containsKey(id)) markers.remove("player:"+id);
        for(Assessment a:current.values()) if(a.kind().priority<2 || a.radius()==Radius.OUTSIDE) markers.remove("player:"+a.player().identity().key());
        for(Assessment a:current.values()) if(a.kind().priority>=2 && a.radius()!=Radius.OUTSIDE && !a.stale()) {
            String key="player:"+a.player().identity().key();
            markers.put(key,new ThreatMarker(key,a.player().identity(),a.player().position(),MarkerType.LIVE,a.player().identity().name()+" | public map",a.outlawCount(),a.lastSeenAt(),a.lastSeenAt()+config.expireSeconds*1000L));
        }
        updateAffiliations(snapshot,self,local,now,config,events);
        var nearby = current.values().stream().filter(a -> a.radius() != Radius.OUTSIDE)
            .sorted(ThreatPresentation.ORDER).toList();
        events.sort(Comparator.comparingInt(Alert::priority).reversed());
        Set<String> fresh=new HashSet<>();
        current.values().stream().filter(a -> !a.stale()).forEach(a -> fresh.add(a.player().identity().key()));
        return new View(exposure.state(),nearby,events,true,true,markerView(fresh,local,now,config));
    }
    public static Kind classify(boolean known, boolean automatic, boolean watched) {
        EnumSet<Kind> kinds = EnumSet.of(Kind.ORDINARY);
        if (known) kinds.add(Kind.KNOWN); if (automatic) kinds.add(Kind.AUTOMATIC); if (watched) kinds.add(Kind.WATCHED);
        return kinds.stream().max(Comparator.comparingInt(k -> k.priority)).orElse(Kind.ORDINARY);
    }
    private boolean ignored(Profile self, Profile other, HunterAlertConfig c) {
        if (self == null || other == null) return false;
        return c.ignoreTown && !self.town().isBlank() && self.town().equalsIgnoreCase(other.town()) || c.ignoreNation && !self.nation().isBlank() && self.nation().equalsIgnoreCase(other.nation());
    }
    private boolean shouldAlert(Kind kind, HunterAlertConfig c) { return kind.priority >= 2 || kind == Kind.WATCHED && c.watchedAlerts || kind == Kind.ORDINARY && c.ordinaryAlerts; }
    private void updateAffiliations(Snapshot s,Identity self, Position local, long now, HunterAlertConfig c, List<Alert> events) {
        List<Position> references=new ArrayList<>(List.of(local));
        Profile own=s.profiles().get(Models.key(self.name()));
        if(own!=null && now-own.updatedAt()<=15*60_000L) s.towns().stream().filter(t -> !own.town().isBlank() && t.name().equalsIgnoreCase(own.town()) && t.anchor().world().equals(local.world())).map(Town::anchor).forEach(references::add);
        Set<String> retained = new HashSet<>();
        for (Profile profile : s.profiles().values()) {
            if(self.matches(profile.identity())) continue;
            boolean known = c.tracked(profile.identity(),Kind.KNOWN);
            boolean auto = c.automatic && s.outlawCounts().getOrDefault(Models.key(profile.identity().name()),0) >= c.minimumOutlawTowns;
            if ((!known && !auto) || now-profile.updatedAt() > 15*60_000L) continue;
            String id = profile.identity().key(); retained.add(id);
            Profile old = affiliations.put(id,profile);
            if (old == null || profile.updatedAt() <= old.updatedAt()) continue;
            if (!old.town().equalsIgnoreCase(profile.town()) && (known ? c.knownTownChanges : c.automaticTownChanges))
                affiliation(s,references,now,c,events,profile,old.town(),profile.town(),false);
            if (!old.nation().equalsIgnoreCase(profile.nation()) && (known ? c.knownNationChanges : c.automaticNationChanges))
                affiliation(s,references,now,c,events,profile,old.nation(),profile.nation(),true);
        }
        affiliations.entrySet().removeIf(e -> now-e.getValue().updatedAt()>15*60_000L);
    }
    private void affiliation(Snapshot s,List<Position> references,long now,HunterAlertConfig c,List<Alert> events,Profile p,String old,String next,boolean nation) {
        String type = nation ? "nation" : "town";
        Town nearest = null; double distance = Double.POSITIVE_INFINITY;
        for (Town town : s.towns()) {
            if (!(nation ? town.nation() : town.name()).equalsIgnoreCase(next) || next.isBlank()) continue;
            for(Position reference:references) {
                var d = reference.distance(town.anchor());
                if (d.isPresent() && d.getAsDouble() < distance) { distance = d.getAsDouble(); nearest = town; }
            }
        }
        if (!next.isBlank() && (nearest == null || distance > c.affiliationRadius)) return;
        String message = p.identity().name()+" changed "+type+": "+(old.isBlank()?"none":old)+" -> "+(next.isBlank()?"none":next)+". ";
        if (nearest != null) {
            message += "Nearest "+nearest.name()+": "+Math.round(distance)+" blocks from you or your town ("+nearest.anchorKind()+"). ";
            String key="affiliation:"+p.identity().key()+":"+type;
            markers.put(key,new ThreatMarker(key,p.identity(),nearest.anchor(),MarkerType.AFFILIATION,p.identity().name()+" -> "+nearest.name()+" | affiliation clue",s.outlawCounts().getOrDefault(Models.key(p.identity().name()),0),now,now+c.affiliationMarkerSeconds*1000L));
        }
        message += "Affiliation does not confirm physical location. Player map location: "+(s.feed().players().stream().anyMatch(m -> m.identity().matches(p.identity())) ? "see public map" : "UNKNOWN");
        emit(events,now,"player:"+p.identity().key()+":affiliation-"+type+":"+Models.key(next),"AFFILIATION WARNING",message,3,c);
    }
    private void emit(List<Alert> events,long now,String key,String title,String message,int priority,HunterAlertConfig c) {
        Alert alert = new Alert(now,key,title,message,priority);
        if (alerts.accept(alert,c.cooldownSeconds*1000L)) events.add(alert);
    }
    private List<ThreatMarker> markerView(Set<String> fresh,Position local,long now,HunterAlertConfig config) {
        markers.entrySet().removeIf(e -> now>=e.getValue().expiresAt() || !(config.tracked(e.getValue().identity(),Kind.KNOWN) || config.automatic && e.getValue().outlaws()>=config.minimumOutlawTowns));
        return markers.values().stream().filter(m -> local!=null && local.world().equals(m.position().world()))
            .map(m -> m.type()==MarkerType.LIVE && !fresh.contains(m.identity().key()) ? new ThreatMarker(m.key(),m.identity(),m.position(),MarkerType.LAST_SEEN,m.identity().name()+" | last public position; location unknown",m.outlaws(),m.observedAt(),m.expiresAt()) : m)
            .sorted(Comparator.<ThreatMarker>comparingInt(m -> m.type()==MarkerType.AFFILIATION ? 0 : 1).thenComparing(Comparator.comparingInt(ThreatMarker::outlaws).reversed())).limit(64).toList();
    }
    public void reset() { exposure.reset(); movement.clear(); alerts.clear(); previous.clear(); affiliations.clear(); markers.clear(); localWorld=""; lastFeed=0; suspended=false; lastConfig=null; }
}
