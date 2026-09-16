package net.earthmc.hunteralert.platform.gui;

import net.earthmc.hunteralert.platform.HunterAlertClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private final HunterAlertClient mod;
    public HudEditorScreen(Screen parent,HunterAlertClient mod) { super(Text.literal("Click or drag to position the HUD")); this.parent=parent; this.mod=mod; }
    @Override protected void init() { addDrawableChild(ButtonWidget.builder(Text.literal("Done"),b -> close()).dimensions(width/2-50,height-30,100,20).build()); }
    private void move(double x,double y) { var c=mod.store.editableCopy(); c.hudX=(int)(x/c.hudScale); c.hudY=(int)(y/c.hudScale); mod.store.save(c); }
    @Override public boolean mouseClicked(Click click,boolean doubled) {
        if(super.mouseClicked(click,doubled)) return true;
        if(click.button()==0) { move(click.x(),click.y()); return true; } return false;
    }
    @Override public boolean mouseDragged(Click click,double dx,double dy) { if(click.button()==0) { move(click.x(),click.y()); return true; } return super.mouseDragged(click,dx,dy); }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext ctx,int mx,int my,float delta) {
        super.render(ctx,mx,my,delta); ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,12,0xFFFFFFFF);
        var c=mod.store.config(); int x=(int)(c.hudX*c.hudScale),y=(int)(c.hudY*c.hudScale);
        ctx.fill(x,y,x+(int)(230*c.hudScale),y+(int)(60*c.hudScale),0xAA223344);
        ctx.drawText(textRenderer,"HUNTER ALERT - preview",x+6,y+6,0xFFFFFFFF,false);
    }
}
