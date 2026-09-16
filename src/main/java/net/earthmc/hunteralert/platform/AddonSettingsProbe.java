package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.model.Models.*;
import net.earthmc.hunteralert.platform.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Loaded only by the addon-enabled development smoke run. */
final class AddonSettingsProbe {
    static void open(Minecraft client,Screen title) { client.setScreen(new net.townymap.gui.TownyMapConfigScreen(title)); }
    static void verify(Minecraft client) {
            var buttons=client.screen.children().stream().filter(e -> e instanceof net.minecraft.client.gui.components.Button).map(e -> (net.minecraft.client.gui.components.Button)e).filter(b -> b.getMessage().getString().equals("Hunter Alert Settings")).toList();
            if(buttons.size()!=1) throw new AssertionError("Missing or duplicate addon settings entry");
            buttons.getFirst().onPress(null);
            if(!(client.screen instanceof SettingsScreen)) throw new AssertionError("Addon settings button did not open Hunter Alert");
            client.screen.onClose();
            if(!(client.screen instanceof net.townymap.gui.TownyMapConfigScreen)) throw new AssertionError("Settings back navigation failed");
            LoggerFactory.getLogger("HunterAlertSmoke").info("ADDON_SETTINGS_PASS: entry, click, return");
    }
}
