package net.earthmc.hunteralert.platform.gui;

import net.earthmc.hunteralert.platform.HunterAlertClient;
import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.lang.reflect.Field;
import java.util.*;

/** Sectioned, paginated settings; config reflection is limited to our own explicit field schema. */
public final class SettingsScreen extends Screen {
    private record Section(String title,String... fields) {}
    private static final List<Section> SECTIONS=List.of(
        new Section("General","enabled","sound","toast","chat"),
        new Section("Detection","critical","warning","awareness","maximum","ordinaryAlerts","watchedAlerts","leaveAlerts","ignoreTown","ignoreNation"),
        new Section("Automatic Hunters","automatic","minimumOutlawTowns"),
        new Section("Exposure","showExposure","exposureTimer","exposedAlert","hiddenAlert"),
        new Section("Movement","movement","approachingThreshold","minimumMovement","movementWindowSeconds"),
        new Section("Affiliation Alerts","knownTownChanges","knownNationChanges","automaticTownChanges","automaticNationChanges","affiliationRadius"),
        new Section("Notifications","sound","toast","chat","flash","soundId","soundVolume","cooldownSeconds"),
        new Section("HUD","hud","compact","hudScale","opacity","hudX","hudY","maxEntries"),
        new Section("Advanced","staleSeconds","expireSeconds","historyLimit","debug")
    );
    private final Screen parent;
    private final HunterAlertClient mod;
    private HunterAlertConfig editing;
    private HunterAlertConfig source;
    private int section,page;
    private final Map<Field,TextFieldWidget> inputs=new LinkedHashMap<>();
    private String error="";
    private int rows;
    public SettingsScreen(Screen parent,HunterAlertClient mod) { super(Text.literal("EarthMC Hunter Alert")); this.parent=parent; this.mod=mod; source=mod.store.config(); editing=mod.store.editableCopy(); }
    private ButtonWidget button(String text,int x,int y,int w,Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(text),b -> action.run()).dimensions(x,y,w,20).build());
    }
    @Override protected void init() {
        if(source != mod.store.config()) { source=mod.store.config(); editing=mod.store.editableCopy(); }
        inputs.clear(); rows=Math.max(1,(height-145)/26);
        int left=Math.max(10,width/2-205), total=Math.min(410,width-20);
        button("<",left,30,25,() -> { if(commitInputs()) { section=Math.floorMod(section-1,SECTIONS.size()); page=0; clearAndInit(); } });
        button(SECTIONS.get(section).title,left+30,30,total-60,() -> { if(commitInputs()) { section=(section+1)%SECTIONS.size(); page=0; clearAndInit(); } });
        button(">",left+total-25,30,25,() -> { if(commitInputs()) { section=(section+1)%SECTIONS.size(); page=0; clearAndInit(); } });
        String[] fields=SECTIONS.get(section).fields;
        for(int i=page*rows;i<Math.min(fields.length,(page+1)*rows);i++) {
            try {
                Field field=HunterAlertConfig.class.getField(fields[i]);
                int y=60+(i-page*rows)*26;
                if(field.getType()==boolean.class) {
                    button(label(field.getName())+": "+field.getBoolean(editing),left,y,total,() -> {
                        if(!commitInputs()) return;
                        try { field.setBoolean(editing,!field.getBoolean(editing)); } catch(IllegalAccessException e) { throw new IllegalStateException(e); }
                        clearAndInit();
                    });
                } else {
                    TextFieldWidget input=new TextFieldWidget(textRenderer,left+total/2,y,total/2,20,Text.literal(label(field.getName())));
                    input.setMaxLength(128); input.setText(String.valueOf(field.get(editing))); addDrawableChild(input); inputs.put(field,input);
                }
            } catch(ReflectiveOperationException e) { throw new IllegalStateException("Invalid config schema",e); }
        }
        button("Previous",left,height-78,80,() -> { if(page>0 && commitInputs()) { page--; clearAndInit(); } });
        button("Next",left+85,height-78,65,() -> { if((page+1)*rows<fields.length && commitInputs()) { page++; clearAndInit(); } });
        button("Hunters",left+155,height-78,75,() -> { if(save()) client.setScreen(new HunterManagerScreen(this,mod)); });
        button("History",left+235,height-78,70,() -> { if(save()) client.setScreen(new HistoryScreen(this,mod)); });
        button("Move HUD",left+310,height-78,Math.max(75,total-310),() -> { if(save()) client.setScreen(new HudEditorScreen(this,mod)); });
        button("Save & close",width/2-100,height-30,125,() -> { if(save()) close(); });
        button("Cancel",width/2+30,height-30,70,this::close);
    }
    private static String label(String field) { String s=field.replaceAll("([a-z])([A-Z])","$1 $2"); return Character.toUpperCase(s.charAt(0))+s.substring(1); }
    private boolean commitInputs() {
        try {
            for(var entry:inputs.entrySet()) {
                Field f=entry.getKey(); String value=entry.getValue().getText().trim();
                if(f.getType()==int.class) f.setInt(editing,Integer.parseInt(value));
                else if(f.getType()==double.class) { double n=Double.parseDouble(value); if(!Double.isFinite(n)) throw new NumberFormatException(); f.setDouble(editing,n); }
                else if(f.getType()==float.class) { float n=Float.parseFloat(value); if(!Float.isFinite(n)) throw new NumberFormatException(); f.setFloat(editing,n); }
                else f.set(editing,value);
            }
            error=""; return true;
        } catch(ReflectiveOperationException|IllegalArgumentException e) { error="Enter valid finite numbers before saving."; return false; }
    }
    private boolean save() { if(!commitInputs()) return false; editing.validate(); mod.store.save(editing); return true; }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta) {
        super.render(ctx,mouseX,mouseY,delta);
        ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,12,0xFFFFFFFF);
        int left=Math.max(10,width/2-205),i=0;
        for(var entry:inputs.entrySet()) {
            ctx.drawText(textRenderer,label(entry.getKey().getName()),left,entry.getValue().getY()+6,0xFFDDDDDD,false); i++;
        }
        ctx.drawCenteredTextWithShadow(textRenderer,error.isEmpty()?"Public map data only. Friends filter / direct map layers unavailable.":error,width/2,height-49,error.isEmpty()?0xFFAAAAAA:0xFFFF7777);
    }
}
