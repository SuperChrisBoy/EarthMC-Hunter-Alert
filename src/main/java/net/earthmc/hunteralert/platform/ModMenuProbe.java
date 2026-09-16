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

/** Loaded only by the Mod Menu-enabled development smoke run. */
final class ModMenuProbe {
    static void verify(Minecraft client,Screen title) {
                    var entry=net.fabricmc.loader.api.FabricLoader.getInstance().getEntrypointContainers("modmenu",com.terraformersmc.modmenu.api.ModMenuApi.class).stream().filter(e -> e.getProvider().getMetadata().getId().equals("hunteralert")).findFirst().orElseThrow();
                    var settings=entry.getEntrypoint().getModConfigScreenFactory().create(title);
                    if(!(settings instanceof SettingsScreen)) throw new AssertionError("Wrong Mod Menu config screen");
                    client.setScreen(settings);
                    LoggerFactory.getLogger("HunterAlertSmoke").info("MOD_MENU_PASS: registered entrypoint and settings factory");
    }
}
