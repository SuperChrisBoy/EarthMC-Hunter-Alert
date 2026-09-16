package net.earthmc.hunteralert.detection;

import net.earthmc.hunteralert.model.Models.*;

public final class ExposureTracker {
    private Exposure state = Exposure.unknown();
    public Exposure update(Visibility visibility, long now) {
        if (visibility != state.state()) state = new Exposure(visibility,now,
            visibility == Visibility.EXPOSED ? now : state.exposedSince(),
            visibility == Visibility.HIDDEN ? now : state.hiddenSince(),
            visibility == Visibility.EXPOSED ? now : state.lastExposedAt(),
            visibility == Visibility.HIDDEN ? now : state.lastHiddenAt());
        return state;
    }
    public Exposure state() { return state; }
    public void reset() { state = Exposure.unknown(); }
}
