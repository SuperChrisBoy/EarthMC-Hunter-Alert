package net.earthmc.hunteralert.model;

import java.util.*;

/** Immutable, Minecraft-independent boundary models. Times are epoch milliseconds. */
public final class Models {
    private Models() {}
    public static String key(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    public record Identity(UUID uuid, String name) {
        public Identity { name = name == null ? "" : name; }
        public String key() { return uuid == null ? "name:" + Models.key(name) : uuid.toString(); }
        public boolean matches(Identity other) {
            return uuid != null && other.uuid != null ? uuid.equals(other.uuid) : Models.key(name).equals(Models.key(other.name));
        }
        public static Identity of(String uuid, String name) {
            try {
                String u = uuid == null ? "" : uuid.replace("-", "");
                if (u.length() == 32) return new Identity(UUID.fromString(u.substring(0,8)+"-"+u.substring(8,12)+"-"+u.substring(12,16)+"-"+u.substring(16,20)+"-"+u.substring(20)), name);
            } catch (IllegalArgumentException ignored) { }
            return new Identity(null, name);
        }
    }
    public record Position(String world, double x, double z) {
        public Position { world = world == null ? "" : world; }
        public OptionalDouble distance(Position other) {
            if (other == null || world.isBlank() || !world.equals(other.world) || !Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(other.x) || !Double.isFinite(other.z)) return OptionalDouble.empty();
            return OptionalDouble.of(Math.hypot(other.x - x, other.z - z));
        }
        public String direction(Position target) {
            if (distance(target).isEmpty()) return "?";
            String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            int sector = (int)Math.round(Math.atan2(target.x-x, -(target.z-z)) / (Math.PI/4));
            return points[Math.floorMod(sector, 8)];
        }
    }
    public record MapPlayer(Identity identity, Position position, long updatedAt) {}
    public record Profile(Identity identity, String town, String nation, long updatedAt) {
        public Profile { town = town == null ? "" : town; nation = nation == null ? "" : nation; }
    }
    public record Town(String name, String nation, Position anchor, String anchorKind) {}
    public record Feed(long receivedAt, long sourceTimestamp, boolean healthy, List<MapPlayer> players) {
        public Feed { players = List.copyOf(players); }
        public static Feed unavailable() { return new Feed(0,0,false,List.of()); }
    }
    public record Snapshot(Feed feed, List<Town> towns, Map<String, Profile> profiles, Map<String,Integer> outlawCounts, long intelligenceAt) {
        public Snapshot { towns = List.copyOf(towns); profiles = Map.copyOf(profiles); outlawCounts = Map.copyOf(outlawCounts); }
    }
    public enum Kind {
        ORDINARY(0), WATCHED(1), AUTOMATIC(2), KNOWN(3);
        public final int priority;
        Kind(int priority) { this.priority = priority; }
    }
    public enum Radius { OUTSIDE, MAXIMUM, AWARENESS, WARNING, CRITICAL }
    public enum Trend { UNKNOWN, STATIONARY, APPROACHING, MOVING_AWAY }
    public enum Visibility { UNKNOWN, EXPOSED, HIDDEN }
    public record Exposure(Visibility state, long since, long exposedSince, long hiddenSince, long lastExposedAt, long lastHiddenAt) {
        public static Exposure unknown() { return new Exposure(Visibility.UNKNOWN,0,0,0,0,0); }
    }
    public record Assessment(MapPlayer player, Kind kind, boolean knownHunter, boolean automaticHunter, boolean watchedPlayer,
                             int outlawCount, double distance, String direction, Radius radius, Trend trend,
                             double distanceChange, long sampleMillis, boolean stale, long lastSeenAt) {}
    public record Alert(long at, String key, String title, String message, int priority) {}
    public record View(Exposure exposure, List<Assessment> nearby, List<Alert> events, boolean healthy, boolean locationKnown) {
        public View { nearby = List.copyOf(nearby); events = List.copyOf(events); }
        public static View empty() { return new View(Exposure.unknown(), List.of(), List.of(), false, false); }
    }
}
