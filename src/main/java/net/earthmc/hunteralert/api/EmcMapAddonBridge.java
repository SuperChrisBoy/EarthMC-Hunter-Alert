package net.earthmc.hunteralert.api;

import net.earthmc.hunteralert.model.Models.Feed;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

/** Publication point for the version-specific feed completion hook. */
public final class EmcMapAddonBridge {
    private static final AtomicReference<Feed> FEED = new AtomicReference<>(Feed.unavailable());
    private static final AtomicLong GENERATION = new AtomicLong();
    private EmcMapAddonBridge() {}
    public static Feed latest() { return FEED.get(); }
    public static long generation() { return GENERATION.get(); }
    public static synchronized void publish(Feed snapshot,long generation) { if(generation==GENERATION.get()) FEED.set(snapshot); }
    public static synchronized void reset() { GENERATION.incrementAndGet(); FEED.set(Feed.unavailable()); }
}
