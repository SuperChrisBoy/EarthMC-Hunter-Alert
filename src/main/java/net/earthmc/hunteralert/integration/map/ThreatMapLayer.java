package net.earthmc.hunteralert.integration.map;

import net.earthmc.hunteralert.model.Models.*;
import java.util.List;

/** Optional platform boundary for temporary threat and affiliation waypoints. */
public interface ThreatMapLayer {
    void publish(List<ThreatMarker> markers, Position local, double radius);
    void clear();
}
