package net.earthmc.hunteralert.api;

import com.google.gson.*;

/** Validates only feed shape/health. Player normalization remains owned by EMC Map Addon. */
public final class FeedValidation {
    private FeedValidation() {}
    public static boolean valid(String body) {
        try {
            if(body==null || body.isBlank()) return false;
            JsonElement root=JsonParser.parseString(body);
            JsonArray players=root.isJsonArray()?root.getAsJsonArray():root.isJsonObject() && root.getAsJsonObject().has("players")?root.getAsJsonObject().getAsJsonArray("players"):null;
            if(players==null) return false;
            for(JsonElement element:players) {
                if(!element.isJsonObject()) return false;
                JsonObject p=element.getAsJsonObject();
                if(p.has("hidden") && p.get("hidden").getAsBoolean()) continue;
                JsonObject position=p.has("position")?p.getAsJsonObject("position"):p;
                if(!p.has("name") || p.get("name").getAsString().isBlank() || position==null || !position.has("x") || !position.has("z") || !Double.isFinite(position.get("x").getAsDouble()) || !Double.isFinite(position.get("z").getAsDouble())) return false;
            }
            return true;
        } catch(RuntimeException malformed) { return false; }
    }
}
