package net.earthmc.hunteralert.platform.mixin;

import net.earthmc.hunteralert.platform.HunterAlertClient;
import net.earthmc.hunteralert.platform.gui.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.townymap.gui.TownyMapConfigScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=TownyMapConfigScreen.class,remap=false)
public abstract class AddonSettingsMixin {
    @Shadow @Final private java.util.List<String> categories;
    @Shadow private void action(String label,Runnable onClick) { throw new AssertionError(); }
    @Shadow private void section(String label) { throw new AssertionError(); }
    @Shadow private void relayout() { throw new AssertionError(); }
    @Inject(method="init",at=@At("RETURN"),remap=false)
    private void hunteralert$settings(CallbackInfo ci) {
        section("Hunter Alert");
        action("Hunter Alert Settings",() -> Minecraft.getInstance().gui.setScreen(new SettingsScreen((Screen)(Object)this,HunterAlertClient.INSTANCE)));
        // Keep the entry within the clickable rail even at a small GUI height.
        categories.remove("Hunter Alert");
        categories.add(Math.min(1,categories.size()),"Hunter Alert");
        relayout();
    }
}
