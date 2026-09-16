package net.earthmc.hunteralert.detection;

import net.earthmc.hunteralert.model.Models.*;

public final class ExposureTracker {
    private Exposure state = Exposure.unknown();
    private long sampledAt;
    public Exposure update(Visibility visibility, long now) {
        return update(visibility,now,60,120);
    }
    public Exposure update(Visibility visibility,long now,int riseSeconds,int fallSeconds) {
        double percent=state.percent();
        if(sampledAt>0 && now>=sampledAt && visibility!=Visibility.UNKNOWN) {
            double elapsed=Math.min(1000,now-sampledAt);
            if(state.state()==Visibility.EXPOSED) percent+=elapsed/(Math.max(1,riseSeconds)*10.0);
            if(state.state()==Visibility.HIDDEN) percent-=elapsed/(Math.max(1,fallSeconds)*10.0);
        }
        sampledAt=now;
        if (visibility != state.state()) state = new Exposure(visibility,now,
            visibility == Visibility.EXPOSED ? now : state.exposedSince(),
            visibility == Visibility.HIDDEN ? now : state.hiddenSince(),
            visibility == Visibility.EXPOSED ? now : state.lastExposedAt(),
            visibility == Visibility.HIDDEN ? now : state.lastHiddenAt(),state.percent());
        state=new Exposure(state.state(),state.since(),state.exposedSince(),state.hiddenSince(),state.lastExposedAt(),state.lastHiddenAt(),Math.clamp(percent,0,100));
        return state;
    }
    public Exposure state() { return state; }
    public void reset() { state = Exposure.unknown(); sampledAt=0; }
}
