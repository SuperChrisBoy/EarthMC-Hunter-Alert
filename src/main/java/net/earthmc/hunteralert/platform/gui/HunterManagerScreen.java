package net.earthmc.hunteralert.platform.gui;

import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.model.Models.*;
import net.earthmc.hunteralert.platform.HunterAlertClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.*;

public final class HunterManagerScreen extends Screen {
    private final Screen parent;
    private final HunterAlertClient mod;
    private TextFieldWidget name,note,search;
    private String nameValue="",noteValue="",query="",error="";
    private int page;
    private boolean automatic;
    private Identity editingIdentity;
    public HunterManagerScreen(Screen parent,HunterAlertClient mod) { super(Text.literal("Known hunters, friendly hunters & watched players")); this.parent=parent; this.mod=mod; }
    private void button(String text,int x,int y,int width,Runnable action) { addDrawableChild(ButtonWidget.builder(Text.literal(text),b -> action.run()).dimensions(x,y,width,20).build()); }
    private TextFieldWidget field(int x,int y,int w,String title,String value) {
        var f=new TextFieldWidget(textRenderer,x,y,w,20,Text.literal(title)); f.setMaxLength(256); f.setText(value); addDrawableChild(f); return f;
    }
    private void capture() { nameValue=name.getText(); noteValue=note.getText(); query=search.getText(); }
    @Override protected void init() {
        int left=width/2-210;
        name=field(left,40,135,"Player name",nameValue); name.setMaxLength(16);
        note=field(left+140,40,275,"Note",noteValue);
        button("Save known hunter",left,65,135,() -> save(Kind.KNOWN));
        button("Save friendly hunter",left+140,65,145,() -> save(Kind.FRIENDLY));
        button("Save watched",left+290,65,125,() -> save(Kind.WATCHED));
        button(automatic?"Show manual":"Automatic list",left+190,height-30,130,() -> { capture(); automatic=!automatic; page=0; clearAndInit(); });
        search=field(left,95,320,"Search",query);
        button("Search",left+325,95,90,() -> { capture(); page=0; clearAndInit(); });
        List<HunterAlertConfig.Tracked> entries=automatic ? mod.view.get().nearby().stream().filter(Assessment::automaticHunter).map(a -> new HunterAlertConfig.Tracked(a.player().identity(),Kind.AUTOMATIC,"Outlawed by "+a.outlawCount()+" towns",true)).toList() : mod.store.config().tracked;
        entries=entries.stream().filter(t -> t.identity().name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int rows=Math.max(1,(height-175)/32), total=entries.size();
        page=Math.min(page,Math.max(0,(total-1)/rows));
        for(int i=page*rows;i<Math.min(total,(page+1)*rows);i++) {
            var entry=entries.get(i); int y=125+(i-page*rows)*32;
            button(entry.identity().name()+" ["+entry.kind().name()+"]",left,y,220,() -> { capture(); nameValue=entry.identity().name(); noteValue=entry.note(); editingIdentity=entry.identity(); clearAndInit(); });
            if(automatic) button("Promote",left+225,y,190,() -> { name.setText(entry.identity().name()); editingIdentity=entry.identity(); save(Kind.KNOWN); });
            else {
                button(entry.enabled()?"Enabled":"Disabled",left+225,y,95,() -> change(entry,false));
                button("Remove",left+325,y,90,() -> change(entry,true));
            }
        }
        button("Previous",left,height-30,90,() -> { capture(); page=Math.max(0,page-1); clearAndInit(); });
        button("Next",left+95,height-30,90,() -> { capture(); if((page+1)*rows<total) page++; clearAndInit(); });
        button("Done",left+325,height-30,90,this::close);
    }
    private void change(HunterAlertConfig.Tracked entry,boolean remove) {
        capture(); var c=mod.store.editableCopy(); c.tracked.removeIf(t -> t.identity().matches(entry.identity()));
        if(!remove) c.tracked.add(new HunterAlertConfig.Tracked(entry.identity(),entry.kind(),entry.note(),!entry.enabled()));
        mod.store.save(c); clearAndInit();
    }
    private void save(Kind kind) {
        capture(); String value=nameValue.trim();
        if(!value.matches("[A-Za-z0-9_]{1,16}")) { error="Enter a valid Minecraft name."; return; }
        Identity identity=editingIdentity != null && editingIdentity.name().equalsIgnoreCase(value) ? editingIdentity : new Identity(null,value);
        identity=mod.view.get().nearby().stream().map(a -> a.player().identity()).filter(id -> id.name().equalsIgnoreCase(value)).findFirst().orElse(identity);
        Identity chosen=identity;
        var c=mod.store.editableCopy(); c.tracked.removeIf(t -> t.identity().matches(chosen));
        c.tracked.add(new HunterAlertConfig.Tracked(chosen,kind,noteValue,true)); mod.store.save(c);
        nameValue=""; noteValue=""; editingIdentity=null; error=""; clearAndInit();
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext ctx,int mx,int my,float delta) {
        super.render(ctx,mx,my,delta); ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,10,0xFFFFFFFF);
        ctx.drawText(textRenderer,"Player name / optional note (select a row to edit)",width/2-210,28,0xFFAAAAAA,false);
        if(!error.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer,error,width/2,height-45,0xFFFF7777);
    }
}
