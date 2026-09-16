package net.earthmc.hunteralert.platform.mixin;

import net.earthmc.hunteralert.api.HudObstacles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class HudTextBoundsMixin {
    @Inject(method="text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",at=@At("HEAD"))
    private void hunteralert$string(Font font,String text,int x,int y,int color,boolean shadow,CallbackInfo ci) {
        if(HudObstacles.capturing() && text!=null) hunteralert$capture(x,y,font.width(text),font.lineHeight);
    }
    @Inject(method="text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",at=@At("HEAD"))
    private void hunteralert$text(Font font,Component text,int x,int y,int color,boolean shadow,CallbackInfo ci) {
        if(HudObstacles.capturing() && text!=null) hunteralert$capture(x,y,font.width(text),font.lineHeight);
    }
    @Unique private void hunteralert$capture(int x,int y,int w,int h) {
        var matrix=((GuiGraphicsExtractor)(Object)this).pose();
        float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
        for(int dx:new int[]{0,w+1}) for(int dy:new int[]{0,h+1}) {
            float px=matrix.m00()*(x+dx)+matrix.m10()*(y+dy)+matrix.m20();
            float py=matrix.m01()*(x+dx)+matrix.m11()*(y+dy)+matrix.m21();
            minX=Math.min(minX,px); minY=Math.min(minY,py); maxX=Math.max(maxX,px); maxY=Math.max(maxY,py);
        }
        HudObstacles.include(new HudObstacles.Rect((int)Math.floor(minX),(int)Math.floor(minY),(int)Math.ceil(maxX-minX),(int)Math.ceil(maxY-minY)));
    }
}
