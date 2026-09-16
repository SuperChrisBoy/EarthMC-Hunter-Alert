package net.earthmc.hunteralert.platform.mixin;

import net.earthmc.hunteralert.platform.HunterAlertClient;
import net.earthmc.hunteralert.detection.ActionBarFilter;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Filter only the exact unwanted action-bar message on an active EarthMC connection. */
@Mixin(InGameHud.class)
public abstract class ActionBarMixin {
    @Shadow private Text overlayMessage;
    @Inject(method="renderOverlayMessage",at=@At("HEAD"),cancellable=true)
    private void hunteralert$hideDefault(CallbackInfo ci) {
        var mod=HunterAlertClient.INSTANCE;
        if(mod!=null && mod.active && mod.store.config().enabled && mod.store.config().hideDefaultActionBar
            && ActionBarFilter.isDefault(overlayMessage==null ? null : overlayMessage.getString())) ci.cancel();
    }
}
