package net.earthmc.hunteralert.detection;

public final class ActionBarFilter {
    public static boolean isDefault(String text) { return "Default".equals(text == null ? null : text.strip()); }
    private ActionBarFilter() {}
}
