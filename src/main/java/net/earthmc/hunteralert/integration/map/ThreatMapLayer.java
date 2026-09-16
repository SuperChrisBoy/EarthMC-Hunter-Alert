package net.earthmc.hunteralert.integration.map;

import net.earthmc.hunteralert.model.Models.*;
import java.util.List;

/** Optional map extension seam. No unverified Xaero rendering injections are installed. */
public interface ThreatMapLayer {
    record Marker(Identity identity, Position position, Kind kind, long lastSeenAt) {
        public float opacity(long now,long timeout) {
            long age=Math.max(0,now-lastSeenAt);
            if (timeout <= 0 || age >= timeout) return 0;
            return age <= 10_000 ? 1 : age <= 30_000 ? .6f : .25f;
        }
    }
    void publish(List<Marker> markers, Position local, double radius);
    void clear();
}
