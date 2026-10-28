package net.earthmc.hunteralert.data;

import com.google.gson.*;
import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.model.Models;
import net.earthmc.hunteralert.model.Models.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Public Squaremap positions and v4 API intelligence. Two lanes keep town sweeps off the feed path. */
public final class DirectEarthMcDataProvider implements EarthMcDataProvider {
    private final URI map, api;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ExecutorService feeds=lane("map"), intelligence=lane("intelligence");
    private volatile Feed feed=Feed.unavailable();
    private volatile Snapshot details=new Snapshot(feed,List.of(),Map.of(),Map.of(),0);
    private long generation, nextFeed, nextDetails, nextProfiles;
    private boolean feedBusy, detailsBusy, profilesBusy, closed;
    private String etag;
    private volatile String localName="";
    @Override public void localIdentity(Identity identity) { localName=identity.name(); }
    public DirectEarthMcDataProvider() { this(URI.create("https://map.earthmc.net/tiles/players.json"),URI.create("https://api.earthmc.net/v4/")); }
    public DirectEarthMcDataProvider(URI map,URI api) { this.map=map; this.api=api; }
    private static ExecutorService lane(String name) { return Executors.newSingleThreadExecutor(r -> { var t=new Thread(r,"HunterAlert-"+name); t.setDaemon(true); return t; }); }
    @Override public synchronized void tick(long now,HunterAlertConfig config) {
        if(closed) return;
        long token=generation;
        if(!feedBusy && now>=nextFeed) {
            feedBusy=true; nextFeed=now+3000;
            feeds.execute(() -> refreshFeed(token));
        }
        if(!detailsBusy && now>=nextDetails) {
            detailsBusy=true; nextDetails=now+60_000;
            var names=config.tracked.stream().filter(HunterAlertConfig.Tracked::enabled).map(t -> t.identity().name()).toList();
            int minimum=config.automatic ? config.minimumOutlawTowns : Integer.MAX_VALUE;
            intelligence.execute(() -> refreshDetails(token,names,minimum));
        }
        if(!detailsBusy && !profilesBusy && details.intelligenceAt()>0 && now>=nextProfiles) {
            profilesBusy=true; nextProfiles=now+60_000;
            var names=config.tracked.stream().filter(HunterAlertConfig.Tracked::enabled).map(t -> t.identity().name()).toList();
            int minimum=config.automatic ? config.minimumOutlawTowns : Integer.MAX_VALUE;
            intelligence.execute(() -> refreshProfiles(token,names,minimum));
        }
    }
    private void refreshProfiles(long token,List<String> tracked,int minimum) {
        try {
            Set<String> wanted=new LinkedHashSet<>(tracked);
            if(!localName.isBlank()) wanted.add(localName);
            details.outlawCounts().forEach((name,count) -> { if(count>=minimum) wanted.add(name); });
            feed.players().forEach(p -> wanted.add(p.identity().name()));
            List<String> names=new ArrayList<>(wanted); Map<String,Profile> profiles=new HashMap<>();
            for(int offset=0;offset<names.size();offset+=100) {
                var batch=fetch("players",names.subList(offset,Math.min(offset+100,names.size())),token).getAsJsonArray();
                long now=System.currentTimeMillis();
                for(var e:batch) {
                    var p=e.getAsJsonObject(); String name=text(p,"name");
                    if(name.isBlank() || !p.has("town") || !p.has("nation")) continue;
                    profiles.put(Models.key(name),new Profile(Identity.of(text(p,"uuid"),name),reference(p,"town"),reference(p,"nation"),now,parsePresence(p)));
                }
            }
            synchronized(this) { if(current(token)) { var old=details; details=new Snapshot(old.feed(),old.towns(),profiles,old.outlawCounts(),old.intelligenceAt()); } }
        } catch(Exception ignored) { /* Missing API results do not imply a departure. */ }
        finally { synchronized(this) { profilesBusy=false; } }
    }
    private synchronized boolean current(long token) { return !closed && generation==token; }
    private void refreshFeed(long token) {
        try {
            var request=request(map);
            String tag; synchronized(this) { tag=etag; }
            if(tag!=null) request.header("If-None-Match",tag);
            var response=http.send(request.GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var body=response.body()) {
                long now=System.currentTimeMillis();
                Feed next;
                if(response.statusCode()==304) {
                    Feed old=feed;
                    if(old.receivedAt()==0) throw new IllegalStateException("304 without cached feed");
                    next=new Feed(now,now,true,old.players().stream().map(p -> new MapPlayer(p.identity(),p.position(),now)).toList());
                } else {
                    if(response.statusCode()!=200) throw new IllegalStateException("Map HTTP "+response.statusCode());
                    next=parseFeed(read(body),now);
                }
                synchronized(this) { if(current(token)) { feed=next; etag=response.headers().firstValue("ETag").orElse(null); } }
            }
        } catch(Exception error) {
            synchronized(this) { if(current(token)) { var old=feed; feed=new Feed(old.receivedAt(),old.sourceTimestamp(),false,old.players()); nextFeed=System.currentTimeMillis()+15_000; } }
        } finally { synchronized(this) { feedBusy=false; } }
    }
    public static Feed parseFeed(String json,long now) {
        var root=JsonParser.parseString(json).getAsJsonObject();
        if(!root.has("players") || !root.get("players").isJsonArray()) throw new IllegalArgumentException("Missing player list");
        List<MapPlayer> players=new ArrayList<>();
        for(var element:root.getAsJsonArray("players")) {
            var p=element.getAsJsonObject();
            if(p.has("hidden") && p.get("hidden").getAsBoolean()) continue;
            String name=text(p,"name"), world=world(text(p,"world"));
            double x=p.get("x").getAsDouble(),z=p.get("z").getAsDouble();
            if(!name.matches("[A-Za-z0-9_]{1,16}") || world.isBlank() || !Double.isFinite(x) || !Double.isFinite(z)) throw new IllegalArgumentException("Invalid map player");
            players.add(new MapPlayer(Identity.of(text(p,"uuid"),name),new Position(world,x,z),now));
        }
        return new Feed(now,now,true,players);
    }
    private void refreshDetails(long token,List<String> tracked,int minimum) {
        try {
            var index=fetch("towns",null,token).getAsJsonArray();
            List<String> names=new ArrayList<>();
            for(var e:index) names.add(text(e.getAsJsonObject(),"name"));
            List<Town> towns=new ArrayList<>(); Map<String,Integer> outlaws=new HashMap<>(); Map<String,Profile> profiles=new HashMap<>();
            for(int offset=0;offset<names.size();offset+=100) {
                var batch=fetch("towns",names.subList(offset,Math.min(offset+100,names.size())),token).getAsJsonArray();
                for(var e:batch) {
                    var t=e.getAsJsonObject(); String name=text(t,"name"),nation=reference(t,"nation");
                    if(name.isBlank() || !t.has("outlaws") || !t.get("outlaws").isJsonArray()) throw new IllegalArgumentException("Incomplete town response");
                    if(t.has("coordinates") && t.get("coordinates").isJsonObject()) {
                        var coords=t.getAsJsonObject("coordinates");
                        if(coords.has("spawn") && coords.get("spawn").isJsonObject()) {
                            var spawn=coords.getAsJsonObject("spawn");
                            towns.add(new Town(name,nation,new Position(world(text(spawn,"world")),spawn.get("x").getAsDouble(),spawn.get("z").getAsDouble()),"town spawn"));
                        }
                    }
                    if(t.has("outlaws") && t.get("outlaws").isJsonArray()) {
                        Set<String> unique=new HashSet<>();
                        for(var o:t.getAsJsonArray("outlaws")) unique.add(Models.key(text(o.getAsJsonObject(),"name")));
                        unique.remove(""); unique.forEach(n -> outlaws.merge(n,1,Integer::sum));
                    }
                }
            }
            Set<String> wanted=new LinkedHashSet<>(tracked);
            if(!localName.isBlank()) wanted.add(localName);
            outlaws.forEach((name,count) -> { if(count>=minimum) wanted.add(name); });
            feed.players().forEach(p -> wanted.add(p.identity().name()));
            names=new ArrayList<>(wanted);
            for(int offset=0;offset<names.size();offset+=100) {
                var batch=fetch("players",names.subList(offset,Math.min(offset+100,names.size())),token).getAsJsonArray();
                long now=System.currentTimeMillis();
                for(var e:batch) {
                    var p=e.getAsJsonObject(); String name=text(p,"name");
                    if(name.isBlank() || !p.has("town") || !p.has("nation")) continue;
                    profiles.put(Models.key(name),new Profile(Identity.of(text(p,"uuid"),name),reference(p,"town"),reference(p,"nation"),now,parsePresence(p)));
                }
            }
            synchronized(this) { if(current(token)) { details=new Snapshot(Feed.unavailable(),towns,profiles,outlaws,System.currentTimeMillis()); nextDetails=System.currentTimeMillis()+600_000; nextProfiles=System.currentTimeMillis()+60_000; } }
        } catch(Exception error) {
            synchronized(this) { if(current(token)) nextDetails=Math.max(nextDetails,System.currentTimeMillis()+60_000); }
        } finally { synchronized(this) { detailsBusy=false; } }
    }
    private JsonElement fetch(String endpoint,List<String> names,long token) throws Exception {
        if(!current(token)) throw new CancellationException();
        // At most one API request per second, well below the public quota.
        Thread.sleep(1000);
        if(!current(token)) throw new CancellationException();
        var request=request(api.resolve(endpoint));
        if(names==null) request.GET();
        else {
            Map<String,Object> template=new LinkedHashMap<>();
            template.put("name",true); template.put("uuid",true); template.put("nation",true);
            if(endpoint.equals("towns")) { template.put("coordinates",true); template.put("outlaws",true); }
            else { template.put("town",true); template.put("status",true); }
            request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(Map.of("query",names,"template",template))));
        }
        var response=http.send(request.build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var body=response.body()) {
            if(response.statusCode()==429) {
                long retry=60;
                try { retry=Long.parseLong(response.headers().firstValue("Retry-After").orElse("60")); } catch(NumberFormatException ignored) {}
                synchronized(this) { if(current(token)) { long until=System.currentTimeMillis()+Math.clamp(retry,60,3600)*1000; nextDetails=Math.max(nextDetails,until); nextProfiles=Math.max(nextProfiles,until); } }
            }
            if(response.statusCode()!=200) throw new IllegalStateException("API HTTP "+response.statusCode());
            return JsonParser.parseString(read(body));
        }
    }
    private static HttpRequest.Builder request(URI uri) { return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent","EarthMC-Hunter-Alert/0.2.0").header("Accept","application/json"); }
    private static String read(java.io.InputStream stream) throws java.io.IOException { byte[] bytes=stream.readNBytes(8_000_001); if(bytes.length>8_000_000) throw new java.io.IOException("Response too large"); return new String(bytes,java.nio.charset.StandardCharsets.UTF_8); }
    public static Presence parsePresence(JsonObject player) {
        var status=player.get("status");
        if(status==null || !status.isJsonObject()) return Presence.UNKNOWN;
        var online=status.getAsJsonObject().get("isOnline");
        if(online==null || !online.isJsonPrimitive() || !online.getAsJsonPrimitive().isBoolean()) return Presence.UNKNOWN;
        return online.getAsBoolean() ? Presence.ONLINE : Presence.OFFLINE;
    }
    private static String text(JsonObject o,String key) { return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : ""; }
    private static String reference(JsonObject o,String key) { return o.has(key) && o.get(key).isJsonObject() ? text(o.getAsJsonObject(key),"name") : ""; }
    public static String world(String value) { return switch(value) { case "earth","minecraft:overworld","minecraft:the_nether" -> "minecraft_overworld"; case "moon","earthmc:moon" -> "earthmc_moon"; case "minecraft:the_end" -> ""; default -> value; }; }
    @Override public Snapshot snapshot() { var d=details; return new Snapshot(feed,d.towns(),d.profiles(),d.outlawCounts(),d.intelligenceAt()); }
    @Override public synchronized void reset() { generation++; feed=Feed.unavailable(); details=new Snapshot(feed,List.of(),Map.of(),Map.of(),0); etag=null; nextFeed=nextDetails=nextProfiles=0; }
    @Override public synchronized void close() { closed=true; generation++; feeds.shutdownNow(); intelligence.shutdownNow(); http.shutdownNow(); }
}
