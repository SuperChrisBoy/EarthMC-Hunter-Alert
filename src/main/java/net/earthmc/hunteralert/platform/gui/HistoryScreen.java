package net.earthmc.hunteralert.platform.gui;

import net.earthmc.hunteralert.platform.HunterAlertClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class HistoryScreen extends Screen {
    private final Screen parent;
    private final HunterAlertClient mod;
    private int page;
    private List<String> lines=List.of();
    public HistoryScreen(Screen parent,HunterAlertClient mod) { super(Component.literal("Local alert history")); this.parent=parent; this.mod=mod; }
    @Override protected void init() {
        List<String> next=new ArrayList<>();
        var events=mod.store.history();
        var time=DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        for(int i=events.size()-1;i>=0;i--) {
            var event=events.get(i);
            next.add(time.format(Instant.ofEpochMilli(event.at()))+"  "+event.title());
            String message=event.message();
            while(!message.isEmpty()) {
                String part=font.plainSubstrByWidth(message,Math.max(80,width-45));
                if(part.isEmpty()) break;
                next.add(part); message=message.substring(part.length());
            }
            next.add("");
        }
        lines=List.copyOf(next);
        addRenderableWidget(Button.builder(Component.literal("Previous"),b -> { page=Math.max(0,page-1); }).bounds(15,height-28,90,20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"),b -> { if((page+1)*rows()<lines.size()) page++; }).bounds(110,height-28,90,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b -> onClose()).bounds(width-105,height-28,90,20).build());
    }
    private int rows() { return Math.max(1,(height-65)/12); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor ctx,int mx,int my,float delta) {
        super.extractRenderState(ctx,mx,my,delta); ctx.centeredText(font,title,width/2,12,0xFFFFFFFF);
        for(int i=page*rows();i<Math.min(lines.size(),(page+1)*rows());i++) ctx.text(font,lines.get(i),15,35+(i-page*rows())*12,0xFFDDDDDD,false);
    }
}
