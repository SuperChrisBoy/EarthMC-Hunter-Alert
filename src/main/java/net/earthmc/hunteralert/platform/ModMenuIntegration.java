package net.earthmc.hunteralert.platform;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.earthmc.hunteralert.platform.gui.SettingsScreen;

public final class ModMenuIntegration implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SettingsScreen(parent,HunterAlertClient.INSTANCE);
    }
}
