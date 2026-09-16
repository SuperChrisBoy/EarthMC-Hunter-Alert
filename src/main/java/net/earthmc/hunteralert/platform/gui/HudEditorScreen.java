package net.earthmc.hunteralert.platform.gui;

import net.earthmc.hunteralert.platform.HunterAlertClient;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private final HunterAlertClient mod;
    public HudEditorScreen(Screen parent,HunterAlertClient mod) { super(Component.literal("MouseButtonEvent or drag to position the HUD")); this.parent=parent; this.mod=mod; }
    @Override protected void init() { addRenderableWidget(Button.builder(Component.literal("Done"),b -> onClose()).bounds(width/2-50,height-30,100,20).build()); }
    private void move(double x,double y) { var c=mod.store.editableCopy(); c.hudX=(int)(x/c.hudScale); c.hudY=(int)(y/c.hudScale); mod.store.save(c); }
    @Override public boolean mouseClicked(MouseButtonEvent MouseButtonEvent,boolean doubled) {
        if(super.mouseClicked(MouseButtonEvent,doubled)) return true;
        if(MouseButtonEvent.button()==0) { move(MouseButtonEvent.x(),MouseButtonEvent.y()); return true; } return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent MouseButtonEvent,double dx,double dy) { if(MouseButtonEvent.button()==0) { move(MouseButtonEvent.x(),MouseButtonEvent.y()); return true; } return super.mouseDragged(MouseButtonEvent,dx,dy); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor ctx,int mx,int my,float delta) {
        super.extractRenderState(ctx,mx,my,delta); ctx.centeredText(font,title,width/2,12,0xFFFFFFFF);
        var c=mod.store.config(); int x=(int)(c.hudX*c.hudScale),y=(int)(c.hudY*c.hudScale);
        ctx.fill(x,y,x+(int)(230*c.hudScale),y+(int)(60*c.hudScale),0xAA223344);
        ctx.text(font,"HUNTER ALERT - preview",x+6,y+6,0xFFFFFFFF,false);
    }
}
