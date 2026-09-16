package net.earthmc.hunteralert.alert;

import net.earthmc.hunteralert.model.Models.Alert;
import java.util.*;

/** State transitions belong to the engine. This adds bounded cooldown protection. */
public final class AlertManager {
    private final Map<String,Long> lastSent = new HashMap<>();
    public boolean accept(Alert alert, long cooldownMillis) {
        Long last = lastSent.get(alert.key());
        if (last != null && alert.at()-last < cooldownMillis) return false;
        lastSent.put(alert.key(),alert.at());
        if (lastSent.size() > 4096) lastSent.entrySet().removeIf(e -> alert.at()-e.getValue() >= cooldownMillis);
        if (lastSent.size() > 4096) lastSent.remove(Collections.min(lastSent.entrySet(),Map.Entry.comparingByValue()).getKey());
        return true;
    }
    public void clear() { lastSent.clear(); }
}
