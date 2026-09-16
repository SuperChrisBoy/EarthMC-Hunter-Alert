package net.earthmc.hunteralert.data;

import net.earthmc.hunteralert.model.Models;
import java.util.*;

/** Boundary adapter for the addon's already batched, cached town-outlaw reverse index. */
public final class OutlawIndex {
    private OutlawIndex() {}
    public static Map<String,Integer> normalize(Map<String,int[]> sharedIndex) {
        Map<String,Integer> result = new HashMap<>();
        sharedIndex.forEach((name,counts) -> { if (counts != null && counts.length > 0) result.put(Models.key(name),Math.max(0,counts[0])); });
        return Map.copyOf(result);
    }
}
