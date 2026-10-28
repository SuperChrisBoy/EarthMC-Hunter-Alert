package net.earthmc.hunteralert.platform.gui;

import net.earthmc.hunteralert.config.HunterAlertConfig;
import net.earthmc.hunteralert.model.Models.*;
import net.earthmc.hunteralert.platform.HunterAlertClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class HunterManagerScreen extends Screen {
    private final Screen parent;
    private final HunterAlertClient mod;
    private EditBox name,note,search;
    private String nameValue="",noteValue="",query="",error="";
    private int page;
    private boolean automatic=true;
    private final Button[] suggestionButtons=new Button[3];
    private List<net.earthmc.hunteralert.data.PlayerSuggestions.Entry> matches=List.of();
    private int suggestionTicks;
    private Identity editingIdentity;
    public HunterManagerScreen(Screen parent,HunterAlertClient mod) { super(Component.literal("Known hunters, friendly hunters & watched players")); this.parent=parent; this.mod=mod; }
    private Button button(String text,int x,int y,int width,Runnable action) { return addRenderableWidget(Button.builder(Component.literal(text),b -> action.run()).bounds(x,y,width,20).build()); }
    private EditBox field(int x,int y,int w,String title,String value) {
        var f=new EditBox(font,x,y,w,20,Component.literal(title)); f.setMaxLength(256); f.setValue(value); addRenderableWidget(f); return f;
    }
    private void capture() { nameValue=name.getValue(); noteValue=note.getValue(); query=search.getValue(); }
    @Override protected void init() {
        int left=width/2-210;
        name=field(left,40,135,"Player name",nameValue); name.setMaxLength(16);
        note=field(left+140,40,275,"Note",noteValue);
        for(int i=0;i<suggestionButtons.length;i++) {
            final int index=i;
            suggestionButtons[i]=button("",left+i*140,65,135,() -> {
                if(index>=matches.size()) return;
                var choice=matches.get(index); editingIdentity=choice.identity();
                name.setValue(choice.identity().name()); name.setFocused(true);
            });
        }
        name.setResponder(value -> refreshSuggestions());
        refreshSuggestions();
        button("Save known hunter",left,95,135,() -> save(Kind.KNOWN));
        button("Save friendly hunter",left+140,95,145,() -> save(Kind.FRIENDLY));
        button("Save watched",left+290,95,125,() -> save(Kind.WATCHED));
        button(automatic?"Saved players":"Candidates",left+190,height-30,130,() -> { capture(); automatic=!automatic; page=0; rebuildWidgets(); });
        search=field(left,125,320,"Search",query);
        button("Search",left+325,125,90,() -> { capture(); page=0; rebuildWidgets(); });
        List<HunterAlertConfig.Tracked> entries=automatic ? mod.suggestions("",true).stream().map(a -> new HunterAlertConfig.Tracked(a.identity(),Kind.AUTOMATIC,"Outlawed by "+a.outlaws()+" towns",true)).toList() : mod.store.config().tracked;
        entries=entries.stream().filter(t -> t.identity().name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int rows=Math.max(1,(height-205)/32), total=entries.size();
        page=Math.min(page,Math.max(0,(total-1)/rows));
        for(int i=page*rows;i<Math.min(total,(page+1)*rows);i++) {
            var entry=entries.get(i); int y=155+(i-page*rows)*32;
            button(automatic ? entry.identity().name()+" | "+entry.note().replace("Outlawed by ","") : entry.identity().name()+" ["+entry.kind().name()+"]",left,y,220,() -> { capture(); nameValue=entry.identity().name(); noteValue=entry.note(); editingIdentity=entry.identity(); rebuildWidgets(); });
            if(automatic) button("Promote",left+225,y,190,() -> { name.setValue(entry.identity().name()); editingIdentity=entry.identity(); save(Kind.KNOWN); });
            else {
                button(entry.enabled()?"Enabled":"Disabled",left+225,y,95,() -> change(entry,false));
                button("Remove",left+325,y,90,() -> change(entry,true));
            }
        }
        button("Previous",left,height-30,90,() -> { capture(); page=Math.max(0,page-1); rebuildWidgets(); });
        button("Next",left+95,height-30,90,() -> { capture(); if((page+1)*rows<total) page++; rebuildWidgets(); });
        button("Done",left+325,height-30,90,this::onClose);
    }
    private void refreshSuggestions() {
        matches=mod.suggestions(name.getValue(),name.getValue().isBlank()).stream().limit(3).toList();
        for(int i=0;i<suggestionButtons.length;i++) {
            var b=suggestionButtons[i]; if(b==null) continue;
            b.active=i<matches.size();
            String label=i<matches.size() ? matches.get(i).identity().name()+" ("+matches.get(i).outlaws()+")" : i==0 ? "No cached matches" : "";
            b.setMessage(Component.literal(font.plainSubstrByWidth(label,125)));
        }
    }
    @Override public void tick() { super.tick(); if(++suggestionTicks%20==0) refreshSuggestions(); }
    private void change(HunterAlertConfig.Tracked entry,boolean remove) {
        capture(); var c=mod.store.editableCopy(); c.tracked.removeIf(t -> t.identity().matches(entry.identity()));
        if(!remove) c.tracked.add(new HunterAlertConfig.Tracked(entry.identity(),entry.kind(),entry.note(),!entry.enabled()));
        mod.store.save(c); rebuildWidgets();
    }
    private void save(Kind kind) {
        capture(); String value=nameValue.trim();
        if(!value.matches("[A-Za-z0-9_]{1,16}")) { error="Enter a valid Minecraft name."; return; }
        Identity identity=editingIdentity != null && editingIdentity.name().equalsIgnoreCase(value) ? editingIdentity : new Identity(null,value);
        identity=mod.suggestions(value,false).stream().map(a -> a.identity()).filter(id -> id.name().equalsIgnoreCase(value)).findFirst().orElse(identity);
        Identity chosen=identity;
        var c=mod.store.editableCopy(); c.tracked.removeIf(t -> t.identity().matches(chosen));
        c.tracked.add(new HunterAlertConfig.Tracked(chosen,kind,noteValue,true)); mod.store.save(c);
        nameValue=""; noteValue=""; editingIdentity=null; error=""; rebuildWidgets();
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor ctx,int mx,int my,float delta) {
        super.extractRenderState(ctx,mx,my,delta); ctx.centeredText(font,title,width/2,10,0xFFFFFFFF);
        ctx.text(font,"Player name / optional note (select a row to edit)",width/2-210,28,0xFFAAAAAA,false);
        if(error.isEmpty()) ctx.centeredText(font,Component.literal(automatic ? "Candidates: at least "+mod.store.config().minimumOutlawTowns+" outlaw towns (cached; not proof of intent)" : "Names show outlaw counts; friendly players are excluded."),width/2,height-45,0xFFAAAAAA);
        if(!error.isEmpty()) ctx.centeredText(font,error,width/2,height-45,0xFFFF7777);
    }
}
