package net.earthmc.hunteralert.platform.mixin;

import net.earthmc.hunteralert.platform.EmcMapAddonDataProvider;
import net.townymap.api.EarthMcApiClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exposes the existing client through the bridge; does not create another network/cache stack. */
@Mixin(value=EarthMcApiClient.class,remap=false)
public abstract class EarthApiMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void hunteralert$shareClient(CallbackInfo ci) { EmcMapAddonDataProvider.sharedEarthApi=(EarthMcApiClient)(Object)this; }
}
