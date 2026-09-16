package net.earthmc.hunteralert.alert;

import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.model.Models.Alert;

public final class AlertSounds {
    public record Cue(String id,float pitch) {}
    public static Cue forAlert(Alert alert,HunterAlertConfig config) {
        if(alert.key().startsWith("self:map-")) return new Cue(config.exposureSoundId,1.25f);
        boolean threat=alert.priority()>=2 && alert.key().startsWith("player:") && (alert.key().contains(":entered-") || alert.key().endsWith(":approaching") || alert.key().contains(":affiliation-") || alert.key().endsWith(":disappeared"));
        return threat ? new Cue(config.threatSoundId,.7f) : new Cue(config.soundId,1);
    }
    private AlertSounds() {}
}
