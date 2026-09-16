package net.earthmc.hunteralert.platform.mixin;

import net.earthmc.hunteralert.platform.HunterAlertClient;
import net.earthmc.hunteralert.platform.gui.SettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.townymap.gui.TownyMapConfigScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=TownyMapConfigScreen.class,remap=false)
public abstract class AddonSettingsMixin {
    @Shadow private void action(String label,Runnable onClick) { throw new AssertionError(); }
    @Inject(method="section",at=@At("RETURN"),remap=false)
    private void hunteralert$settings(String label,CallbackInfo ci) {
        if(label.equals("General")) action("Hunter Alert Settings",() -> MinecraftClient.getInstance().setScreen(new SettingsScreen((Screen)(Object)this,HunterAlertClient.INSTANCE)));
    }
}
