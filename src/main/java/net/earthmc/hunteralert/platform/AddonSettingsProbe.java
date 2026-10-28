package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.model.Models.*;
import net.earthmc.hunteralert.platform.gui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Loaded only by the addon-enabled development smoke run. */
final class AddonSettingsProbe {
    static void open(MinecraftClient client,Screen title) { client.setScreen(new net.townymap.gui.TownyMapConfigScreen(title));
        Screen settings=client.currentScreen;
        var entry=settings.children().stream().filter(e -> e instanceof net.minecraft.client.gui.widget.ButtonWidget).map(e -> (net.minecraft.client.gui.widget.ButtonWidget)e).filter(b -> b.getMessage().getString().equals("Hunter Alert Settings")).findFirst().orElseThrow();
        if(entry.visible) throw new AssertionError("Hunter settings still in General category");
        double left=(settings.width-Math.max(400,Math.min(settings.width-40,560)))/2.0;
        settings.mouseClicked(new net.minecraft.client.gui.Click(left+10,87,new net.minecraft.client.input.MouseInput(0,0)),false);
        if(!entry.visible) throw new AssertionError("Hunter Alert sidebar category did not open");
        LoggerFactory.getLogger("HunterAlertSmoke").info("HUNTER_SIDEBAR_PASS: separate category, visible, click");
    }
    static void verify(MinecraftClient client) {
            var buttons=client.currentScreen.children().stream().filter(e -> e instanceof net.minecraft.client.gui.widget.ButtonWidget).map(e -> (net.minecraft.client.gui.widget.ButtonWidget)e).filter(b -> b.getMessage().getString().equals("Hunter Alert Settings")).toList();
            if(buttons.size()!=1) throw new AssertionError("Missing or duplicate addon settings entry");
            buttons.getFirst().onPress(null);
            if(!(client.currentScreen instanceof SettingsScreen)) throw new AssertionError("Addon settings button did not open Hunter Alert");
            client.currentScreen.close();
            if(!(client.currentScreen instanceof net.townymap.gui.TownyMapConfigScreen)) throw new AssertionError("Settings back navigation failed");
            LoggerFactory.getLogger("HunterAlertSmoke").info("ADDON_SETTINGS_PASS: entry, click, return");
    }
}
