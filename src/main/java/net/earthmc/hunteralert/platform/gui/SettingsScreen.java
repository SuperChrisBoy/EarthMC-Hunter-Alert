package net.earthmc.hunteralert.platform.gui;

import net.earthmc.hunteralert.platform.HunterAlertClient;
import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Field;
import java.util.*;

/** Sectioned, paginated settings; config reflection is limited to our own explicit field schema. */
public final class SettingsScreen extends Screen {
    private record Section(String title,String... fields) {}
    private static final List<Section> SECTIONS=List.of(
        new Section("General","enabled","sound","toast","chat"),
        new Section("Detection","critical","warning","awareness","maximum","ordinaryAlerts","watchedAlerts","leaveAlerts","ignoreTown","ignoreNation"),
        new Section("Automatic Hunters","automatic","minimumOutlawTowns"),
        new Section("Exposure","showExposure","exposureRiseSeconds","exposureFallSeconds","exposureTimer","exposedAlert","hiddenAlert"),
        new Section("Movement","movement","approachingThreshold","minimumMovement","movementWindowSeconds"),
        new Section("Affiliation Alerts","knownTownChanges","knownNationChanges","automaticTownChanges","automaticNationChanges","affiliationRadius"),
        new Section("Notifications","sound","toast","chat","flash","soundId","exposureSoundId","threatSoundId","soundVolume","cooldownSeconds"),
        new Section("HUD","hideDefaultActionBar","mapHighlights","worldMarkers","affiliationMarkerSeconds","hud","avoidHudOverlap","compact","hudScale","opacity","hudX","hudY","maxEntries"),
        new Section("Advanced","staleSeconds","expireSeconds","historyLimit","debug")
    );
    private final Screen parent;
    private final HunterAlertClient mod;
    private HunterAlertConfig editing;
    private HunterAlertConfig source;
    private int section,page;
    private final Map<Field,EditBox> inputs=new LinkedHashMap<>();
    private String error="";
    private int rows;
    public SettingsScreen(Screen parent,HunterAlertClient mod) { super(Component.literal("EarthMC Hunter Alert")); this.parent=parent; this.mod=mod; source=mod.store.config(); editing=mod.store.editableCopy(); }
    private Button button(String text,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text),b -> action.run()).bounds(x,y,w,20).build());
    }
    @Override protected void init() {
        if(source != mod.store.config()) { source=mod.store.config(); editing=mod.store.editableCopy(); }
        inputs.clear(); rows=Math.max(1,(height-145)/26);
        int left=Math.max(10,width/2-205), total=Math.min(410,width-20);
        button("<",left,30,25,() -> { if(commitInputs()) { section=Math.floorMod(section-1,SECTIONS.size()); page=0; rebuildWidgets(); } });
        button(SECTIONS.get(section).title,left+30,30,total-60,() -> { if(commitInputs()) { section=(section+1)%SECTIONS.size(); page=0; rebuildWidgets(); } });
        button(">",left+total-25,30,25,() -> { if(commitInputs()) { section=(section+1)%SECTIONS.size(); page=0; rebuildWidgets(); } });
        String[] fields=SECTIONS.get(section).fields;
        for(int i=page*rows;i<Math.min(fields.length,(page+1)*rows);i++) {
            try {
                Field field=HunterAlertConfig.class.getField(fields[i]);
                int y=60+(i-page*rows)*26;
                if(field.getType()==boolean.class) {
                    button(label(field.getName())+": "+field.getBoolean(editing),left,y,total,() -> {
                        if(!commitInputs()) return;
                        try { field.setBoolean(editing,!field.getBoolean(editing)); } catch(IllegalAccessException e) { throw new IllegalStateException(e); }
                        rebuildWidgets();
                    });
                } else {
                    EditBox input=new EditBox(font,left+total/2,y,total/2,20,Component.literal(label(field.getName())));
                    input.setMaxLength(128); input.setValue(String.valueOf(field.get(editing))); addRenderableWidget(input); inputs.put(field,input);
                }
            } catch(ReflectiveOperationException e) { throw new IllegalStateException("Invalid config schema",e); }
        }
        button("Previous",left,height-78,80,() -> { if(page>0 && commitInputs()) { page--; rebuildWidgets(); } });
        button("Next",left+85,height-78,65,() -> { if((page+1)*rows<fields.length && commitInputs()) { page++; rebuildWidgets(); } });
        button("Hunters",left+155,height-78,75,() -> { if(save()) minecraft.gui.setScreen(new HunterManagerScreen(this,mod)); });
        button("History",left+235,height-78,70,() -> { if(save()) minecraft.gui.setScreen(new HistoryScreen(this,mod)); });
        button("Move HUD",left+310,height-78,Math.max(75,total-310),() -> { if(save()) minecraft.gui.setScreen(new HudEditorScreen(this,mod)); });
        button("Save & close",width/2-100,height-30,125,() -> { if(save()) onClose(); });
        button("Cancel",width/2+30,height-30,70,this::onClose);
    }
    private static String label(String field) { if(field.equals("worldMarkers")) return "World labels (standalone)"; String s=field.replaceAll("([a-z])([A-Z])","$1 $2"); return Character.toUpperCase(s.charAt(0))+s.substring(1); }
    private boolean commitInputs() {
        try {
            for(var entry:inputs.entrySet()) {
                Field f=entry.getKey(); String value=entry.getValue().getValue().trim();
                if(f.getType()==int.class) f.setInt(editing,Integer.parseInt(value));
                else if(f.getType()==double.class) { double n=Double.parseDouble(value); if(!Double.isFinite(n)) throw new NumberFormatException(); f.setDouble(editing,n); }
                else if(f.getType()==float.class) { float n=Float.parseFloat(value); if(!Float.isFinite(n)) throw new NumberFormatException(); f.setFloat(editing,n); }
                else f.set(editing,value);
            }
            error=""; return true;
        } catch(ReflectiveOperationException|IllegalArgumentException e) { error="Enter valid finite numbers before saving."; return false; }
    }
    private boolean save() { if(!commitInputs()) return false; editing.validate(); mod.store.save(editing); return true; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor ctx,int mouseX,int mouseY,float delta) {
        super.extractRenderState(ctx,mouseX,mouseY,delta);
        ctx.centeredText(font,title,width/2,12,0xFFFFFFFF);
        int left=Math.max(10,width/2-205),i=0;
        for(var entry:inputs.entrySet()) {
            ctx.text(font,label(entry.getKey().getName()),left,entry.getValue().getY()+6,0xFFDDDDDD,false); i++;
        }
        ctx.centeredText(font,error.isEmpty()?"Exposure meter, not attack odds. Clues are not locations.":error,width/2,height-49,error.isEmpty()?0xFFAAAAAA:0xFFFF7777);
    }
}
