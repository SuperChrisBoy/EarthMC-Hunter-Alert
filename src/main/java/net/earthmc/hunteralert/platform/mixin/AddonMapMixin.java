package net.earthmc.hunteralert.platform.mixin;

import net.earthmc.hunteralert.platform.AddonMapOverlay;
import net.minecraft.client.gui.DrawContext;
import net.townymap.TownyMapMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=TownyMapMod.class,remap=false)
public class AddonMapMixin {
    @Inject(method="renderWorldMapLatePass",at=@At("RETURN"),remap=false)
    private static void hunteralert$map(DrawContext ctx,double cameraX,double cameraZ,double scale,int width,int height,CallbackInfo ci) {
        AddonMapOverlay.render(ctx,cameraX,cameraZ,scale,width,height);
    }
}
