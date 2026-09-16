package net.earthmc.hunteralert.config;

import net.earthmc.hunteralert.model.Models.*;
import java.util.*;

public final class HunterAlertConfig implements Cloneable {
    public boolean enabled = true, sound = true, toast = true, chat = false, flash = true;
    public double critical = 500, warning = 1500, awareness = 3000, maximum = 5000;
    public boolean ordinaryAlerts = false, watchedAlerts = true, leaveAlerts = true;
    public boolean ignoreTown = false, ignoreNation = false;
    public boolean automatic = true;
    public int minimumOutlawTowns = 10;
    public boolean showExposure = true, exposureTimer = true, exposedAlert = true, hiddenAlert = false;
    public int exposureRiseSeconds=60, exposureFallSeconds=120;
    public boolean mapHighlights=true, worldMarkers=true;
    public int affiliationMarkerSeconds=300;
    public boolean movement = true;
    public double minimumMovement = 30, approachingThreshold = 60;
    public int movementWindowSeconds = 10, staleSeconds = 15, expireSeconds = 120, cooldownSeconds = 30;
    public boolean knownTownChanges = true, knownNationChanges = true, automaticTownChanges = true, automaticNationChanges = true;
    public double affiliationRadius = 5000;
    public boolean hud = true, compact = false, avoidHudOverlap = true;
    public double hudScale = 1, opacity = .75;
    public int hudX = 12, hudY = 12, maxEntries = 5, historyLimit = 1000;
    public String soundId = "minecraft:block.note_block.bell";
    public float soundVolume = .7f;
    public boolean debug = false;
    public List<Tracked> tracked = new ArrayList<>();
    public record Tracked(Identity identity, Kind kind, String note, boolean enabled) {}
    public HunterAlertConfig copy() {
        try { var copy=(HunterAlertConfig)super.clone(); copy.tracked=new ArrayList<>(tracked); return copy; }
        catch(CloneNotSupportedException impossible) { throw new AssertionError(impossible); }
    }

    public void validate() {
        exposureRiseSeconds=Math.clamp(exposureRiseSeconds,1,3600);
        exposureFallSeconds=Math.clamp(exposureFallSeconds,1,3600);
        affiliationMarkerSeconds=Math.clamp(affiliationMarkerSeconds,30,1800);
        critical = finite(critical,500,1,1_000_000);
        warning = finite(warning,1500,critical,1_000_000);
        awareness = finite(awareness,3000,warning,1_000_000);
        maximum = finite(maximum,5000,awareness,1_000_000);
        affiliationRadius = finite(affiliationRadius,5000,1,1_000_000);
        minimumOutlawTowns = Math.max(1,minimumOutlawTowns);
        minimumMovement = finite(minimumMovement,30,1,100_000);
        approachingThreshold = finite(approachingThreshold,60,minimumMovement,100_000);
        movementWindowSeconds = Math.clamp(movementWindowSeconds,2,60);
        staleSeconds = Math.clamp(staleSeconds,3,120);
        expireSeconds = Math.clamp(expireSeconds,staleSeconds,3600);
        cooldownSeconds = Math.clamp(cooldownSeconds,0,3600);
        historyLimit = Math.clamp(historyLimit,100,2000);
        maxEntries = Math.clamp(maxEntries,1,20);
        hudScale = finite(hudScale,1,.5,3);
        opacity = finite(opacity,.75,0,1);
        hudX = Math.max(0,hudX); hudY = Math.max(0,hudY);
        soundVolume = (float)finite(soundVolume,.7,0,1);
        if (soundId == null || !soundId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) soundId = "minecraft:block.note_block.bell";
        if (tracked == null) tracked = new ArrayList<>();
        tracked = new ArrayList<>(tracked.stream().filter(t -> t != null && t.identity != null && t.identity.name().matches("[A-Za-z0-9_]{1,16}") && (t.kind == Kind.KNOWN || t.kind == Kind.WATCHED)).limit(2000).toList());
    }
    private static double finite(double n,double fallback,double min,double max) { return Double.isFinite(n) ? Math.clamp(n,min,max) : fallback; }
    public boolean tracked(Identity id, Kind kind) { return tracked.stream().anyMatch(t -> t.enabled && t.kind == kind && t.identity.matches(id)); }
    public Radius radius(double distance) {
        if (distance <= critical) return Radius.CRITICAL;
        if (distance <= warning) return Radius.WARNING;
        if (distance <= awareness) return Radius.AWARENESS;
        return distance <= maximum ? Radius.MAXIMUM : Radius.OUTSIDE;
    }
}
