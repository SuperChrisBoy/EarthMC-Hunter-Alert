package net.earthmc.hunteralert.platform.mixin;

import net.earthmc.hunteralert.api.HudObstacles;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="xaero.common.minimap.render.MinimapRenderer",remap=false)
public class MinimapBoundsMixin {
    @Inject(method="renderOutsidePip",at=@At("HEAD"),remap=false)
    private void hunteralert$bounds(@Coerce Object session,int x,int y,int sw,int sh,double screenScale,float minimapScale,int configuredWidth,float delta,DrawContext ctx,CallbackInfo ci) {
        HudObstacles.begin();
        double scale=screenScale/Math.max(.0001,minimapScale);
        int size=configuredWidth;
        try {
            Object processor=session.getClass().getMethod("getProcessor").invoke(session);
            size=((Number)processor.getClass().getMethod("getMinimapSize").invoke(processor)).intValue();
        } catch(ReflectiveOperationException ignored) {}
        int left=(int)Math.floor(x+6/scale),top=(int)Math.floor(y+6/scale),side=(int)Math.ceil((size/2.0+6)/scale);
        HudObstacles.include(new HudObstacles.Rect(left-6,top-6,side+12,side+12));
    }
}
