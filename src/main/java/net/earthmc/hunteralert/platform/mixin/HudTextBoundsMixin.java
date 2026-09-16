package net.earthmc.hunteralert.platform.mixin;

import net.earthmc.hunteralert.api.HudObstacles;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class HudTextBoundsMixin {
    @Inject(method="drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V",at=@At("HEAD"))
    private void hunteralert$string(TextRenderer font,String text,int x,int y,int color,boolean shadow,CallbackInfo ci) {
        if(HudObstacles.capturing() && text!=null) hunteralert$capture(x,y,font.getWidth(text),font.fontHeight);
    }
    @Inject(method="drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",at=@At("HEAD"))
    private void hunteralert$text(TextRenderer font,Text text,int x,int y,int color,boolean shadow,CallbackInfo ci) {
        if(HudObstacles.capturing() && text!=null) hunteralert$capture(x,y,font.getWidth(text),font.fontHeight);
    }
    @Unique private void hunteralert$capture(int x,int y,int w,int h) {
        var matrix=((DrawContext)(Object)this).getMatrices();
        float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
        for(int dx:new int[]{0,w+1}) for(int dy:new int[]{0,h+1}) {
            float px=matrix.m00()*(x+dx)+matrix.m10()*(y+dy)+matrix.m20();
            float py=matrix.m01()*(x+dx)+matrix.m11()*(y+dy)+matrix.m21();
            minX=Math.min(minX,px); minY=Math.min(minY,py); maxX=Math.max(maxX,px); maxY=Math.max(maxY,py);
        }
        HudObstacles.include(new HudObstacles.Rect((int)Math.floor(minX),(int)Math.floor(minY),(int)Math.ceil(maxX-minX),(int)Math.ceil(maxY-minY)));
    }
}
