package net.earthmc.hunteralert.detection;

import net.earthmc.hunteralert.model.Models.*;
import java.util.Comparator;

public final class ThreatPresentation {
    public static final Comparator<Assessment> ORDER=Comparator.<Assessment>comparingInt(a -> a.kind().priority>=2 ? 1 : 0).reversed()
        .thenComparing(Comparator.comparingInt(Assessment::outlawCount).reversed())
        .thenComparing(Comparator.<Assessment>comparingInt(a -> a.kind().priority).reversed())
        .thenComparing(Assessment::stale).thenComparingDouble(Assessment::distance);
    public static int color(Assessment a) {
        if(a.stale()) return 0xFFAAAAAA;
        if(a.kind().priority<2) return a.kind()==Kind.WATCHED ? 0xFF77CCFF : 0xFFDDDDDD;
        return switch(a.radius()) { case CRITICAL -> 0xFFFF5555; case WARNING -> 0xFFFF9955; default -> 0xFFFFDD55; };
    }
    private ThreatPresentation() {}
}
