package net.earthmc.hunteralert.data;

import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.model.Models;
import net.earthmc.hunteralert.model.Models.*;
import java.util.*;

/** Local cached suggestions only: typing never starts an API request. */
public final class PlayerSuggestions {
    public record Entry(Identity identity,int outlaws) {}
    public static List<Entry> find(Snapshot snapshot,List<Identity> online,HunterAlertConfig config,Identity self,String prefix,boolean candidates) {
        Map<String,Identity> names=new HashMap<>();
        snapshot.outlawCounts().keySet().forEach(n -> add(names,new Identity(null,n)));
        snapshot.profiles().values().forEach(p -> add(names,p.identity()));
        snapshot.feed().players().forEach(p -> add(names,p.identity()));
        config.tracked.forEach(p -> add(names,p.identity()));
        online.forEach(p -> add(names,p));
        String query=Models.key(prefix);
        var entries=names.values().stream().filter(id -> self==null || !self.matches(id))
            .filter(id -> Models.key(id.name()).startsWith(query))
            .map(id -> new Entry(id,snapshot.outlawCounts().getOrDefault(Models.key(id.name()),0)))
            .filter(e -> !candidates || e.outlaws()>=config.minimumOutlawTowns && !config.tracked(e.identity(),Kind.FRIENDLY));
        Comparator<Entry> order=Comparator.comparing(e -> Models.key(e.identity().name()));
        if(candidates || query.isBlank()) order=Comparator.comparingInt(Entry::outlaws).reversed().thenComparing(order);
        return entries.sorted(order).toList();
    }
    private static void add(Map<String,Identity> names,Identity id) {
        if(id.name().matches("[A-Za-z0-9_]{1,16}")) names.merge(Models.key(id.name()),id,(a,b) -> b.uuid()!=null || a.uuid()==null ? b : a);
    }
    private PlayerSuggestions() {}
}
