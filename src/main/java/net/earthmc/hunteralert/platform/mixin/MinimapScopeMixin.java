package net.earthmc.hunteralert.platform.mixin;

import net.earthmc.hunteralert.api.HudObstacles;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="xaero.hud.minimap.module.MinimapRenderer",remap=false)
public class MinimapScopeMixin {
    @Inject(method="render",at=@At("RETURN"),remap=false)
    private void hunteralert$end(CallbackInfo ci) { HudObstacles.end(); }
}
