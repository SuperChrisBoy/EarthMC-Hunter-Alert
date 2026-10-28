package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.api.EmcMapAddonBridge;
import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.data.*;
import net.earthmc.hunteralert.model.Models;
import net.earthmc.hunteralert.model.Models.*;
import net.townymap.TownyMapMod;
import net.townymap.api.EarthMcApiClient;
import net.townymap.api.SquaremapApiClient;
import net.townymap.model.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Uses the addon's existing clients, gates and caches. Never owns an HTTP client. */
public final class EmcMapAddonDataProvider implements EarthMcDataProvider {
    public static volatile EarthMcApiClient sharedEarthApi;
    private record Raw(SquaremapApiClient api, Map<String,List<TownData>> towns, Map<String,EarthMcPlayerData> players, Map<String,int[]> outlaws, long at) {}
    private volatile Raw raw = new Raw(null,Map.of(),Map.of(),Map.of(),0);
    private Map<String,List<TownData>> lastTowns=Map.of();
    private List<Town> normalizedTowns=List.of();
    private final AtomicReference<Map<String,Profile>> trackedProfiles = new AtomicReference<>(Map.of());
    private long lastCapture, lastTrackedRefresh;
    private volatile boolean trackedLoading;
    private Map<String,EarthMcPlayerData> lastPlayers = Map.of();
    private Map<String,Profile> normalizedPlayers = Map.of();
    private Map<String,int[]> lastOutlaws = Map.of();
    private Map<String,Integer> normalizedOutlaws = Map.of();

    /** Main-thread cache access only; all HTTP below is scheduled by upstream. */
    public void tick(long now, HunterAlertConfig config) {
        var api = TownyMapMod.getApiClient();
        if (api == null) return;
        api.tickPlayers();
        api.tickTownMarkers();
        if (now-lastCapture < 10_000) return;
        lastCapture=now;
        var outlaws = config.automatic ? TownyMapMod.outlawTrustedCounts() : Map.<String,int[]>of();
        var players = TownyMapMod.onlinePlayerStats();
        Map<String,List<TownData>> towns=new HashMap<>();
        // Historical map browsing must never replace live affiliation geography.
        if(api.isArchiveActive()) towns.putAll(raw.towns);
        else {
            Set<String> worlds=new HashSet<>(TownyMapMod.squaremapWorlds().keySet());
            worlds.add(TownyMapMod.WORLD_OVERWORLD); worlds.add(TownyMapMod.WORLD_MOON);
            for(String world:worlds) towns.put(world,api.getTowns(world));
        }
        raw = new Raw(api,Map.copyOf(towns),players,outlaws,now);
        if (sharedEarthApi != null && !trackedLoading && now-lastTrackedRefresh >= 60_000) {
            Set<String> names = new LinkedHashSet<>();
            config.tracked.stream().filter(HunterAlertConfig.Tracked::enabled).forEach(t -> names.add(t.identity().name()));
            outlaws.forEach((name,count) -> { if (count.length > 0 && count[0] >= config.minimumOutlawTowns) names.add(name); });
            if (!names.isEmpty()) {
                trackedLoading=true; lastTrackedRefresh=now;
                sharedEarthApi.fetchPlayers(new ArrayList<>(names)).whenComplete((result,error) -> {
                    if (error == null && result != null) trackedProfiles.updateAndGet(old -> {
                        long received=System.currentTimeMillis();
                        Map<String,Profile> next=new HashMap<>();
                        next.putAll(normalize(result,received,true)); return Map.copyOf(next);
                    });
                    trackedLoading=false;
                });
            }
        }
    }
    private static Map<String,Profile> normalize(Map<String,EarthMcPlayerData> source,long at,boolean explicitRefresh) {
        Map<String,Profile> result = new HashMap<>();
        source.values().forEach(p -> result.put(Models.key(p.name()),new Profile(Identity.of(p.uuid(),p.name()),p.townName(),p.nationName(),at,p.online() ? Presence.ONLINE : explicitRefresh && p.lastOnlineMs()>0 ? Presence.OFFLINE : Presence.UNKNOWN)));
        return Map.copyOf(result);
    }
    @Override public String mapWorld(String dimension) { return TownyMapMod.playerWorldResolved(); }
    @Override public Snapshot snapshot() {
        Raw current=raw;
        if(current.towns != lastTowns) {
            lastTowns=current.towns;
            List<Town> towns=new ArrayList<>();
            current.towns.forEach((world,list) -> {
                for(TownData town:list) {
                    String nation=current.api.getTownNation(town.key());
                    if(nation==null) nation="";
                    if(nation.startsWith("Capital of ")) nation=nation.substring(11);
                    towns.add(new Town(town.name(),nation,new Position(world,town.centerX(),town.centerZ()),"claim bounding centre"));
                }
            });
            normalizedTowns=List.copyOf(towns);
        }
        if (current.players != lastPlayers) { lastPlayers=current.players; normalizedPlayers=normalize(current.players,current.at,false); }
        if (current.outlaws != lastOutlaws) { lastOutlaws=current.outlaws; normalizedOutlaws=OutlawIndex.normalize(current.outlaws); }
        Map<String,Profile> profiles = new HashMap<>(normalizedPlayers);
        trackedProfiles.get().forEach((k,p) -> profiles.merge(k,p,(a,b) -> a.updatedAt() > b.updatedAt() ? a : b));
        return new Snapshot(EmcMapAddonBridge.latest(),normalizedTowns,profiles,normalizedOutlaws,current.at);
    }
}
