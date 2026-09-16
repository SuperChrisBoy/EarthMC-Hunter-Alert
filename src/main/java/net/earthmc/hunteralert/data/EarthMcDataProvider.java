package net.earthmc.hunteralert.data;

import net.earthmc.hunteralert.model.Models.Snapshot;

/** Shared boundary for direct EarthMC HTTP and optional addon cache providers. */
public interface EarthMcDataProvider {
    Snapshot snapshot();
    default void tick(long now, net.earthmc.hunteralert.config.HunterAlertConfig config) {}
    default void reset() {}
    default void close() {}
    default void localIdentity(net.earthmc.hunteralert.model.Models.Identity identity) {}
    default String mapWorld(String dimension) { return DirectEarthMcDataProvider.world(dimension); }
}
