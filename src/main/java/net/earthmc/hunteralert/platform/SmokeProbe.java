package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.model.Models.*;
import net.earthmc.hunteralert.platform.gui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Explicit development-only smoke run (-Psmoke), isolated from production data and activation. */
final class SmokeProbe {
    private static int ticks,stage;
    private static boolean started;
    private static Screen title;
    private static java.util.concurrent.CompletableFuture<Void> feedTest;
    static void tick(MinecraftClient client,HunterAlertClient mod) {
        if(!mod.store.ready() || client.getOverlay()!=null) return;
        if(!started) {
            if(!(client.currentScreen instanceof TitleScreen)) { client.setScreen(new TitleScreen()); return; }
            title=client.currentScreen; started=true;
            LoggerFactory.getLogger("HunterAlertSmoke").info("TITLE_READY");
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaerominimap")) {
                try {
                    Class.forName("xaero.hud.minimap.module.MinimapRenderer");
                    Class.forName("xaero.common.minimap.render.MinimapRenderer");
                    LoggerFactory.getLogger("HunterAlertSmoke").info("OPTIONAL_HUD_MIXINS_LOADED");
                } catch(ClassNotFoundException e) { throw new AssertionError(e); }
            }
            feedTest=net.earthmc.hunteralert.platform.mixin.OptionalMixinPlugin.addon() ? FeedIntegrationProbe.start() : java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        ticks++;
        if(stage==7 && ticks==45 && net.earthmc.hunteralert.platform.mixin.OptionalMixinPlugin.addon()) {
            AddonSettingsProbe.verify(client);
        }
        if(stage==2 && ticks==15) {
            var input=client.currentScreen.children().stream().filter(e -> e instanceof net.minecraft.client.gui.widget.TextFieldWidget).map(e -> (net.minecraft.client.gui.widget.TextFieldWidget)e).filter(e -> e.getMessage().getString().equals("Player name")).findFirst().orElseThrow();
            input.setText("cAn");
            var choice=client.currentScreen.children().stream().filter(e -> e instanceof net.minecraft.client.gui.widget.ButtonWidget).map(e -> (net.minecraft.client.gui.widget.ButtonWidget)e).filter(b -> b.active && b.getMessage().getString().startsWith("CandidateAlpha (")).findFirst().orElseThrow();
            choice.onPress(null);
            if(!input.getText().equals("CandidateAlpha")) throw new AssertionError("Autocomplete did not fill exact name");
            long candidates=client.currentScreen.children().stream().filter(e -> e instanceof net.minecraft.client.gui.widget.ButtonWidget).map(e -> (net.minecraft.client.gui.widget.ButtonWidget)e).filter(b -> b.getMessage().getString().contains(" towns")).count();
            if(candidates<1 || mod.suggestions("",true).size()!=2) throw new AssertionError("Candidate threshold list mismatch: "+candidates);
            LoggerFactory.getLogger("HunterAlertSmoke").info("HUNTER_SUGGESTIONS_PASS: prefix, selection, global candidates, threshold");
        }
        if(stage==5 && ticks==45) { var c=mod.store.editableCopy(); c.logView=false; mod.store.save(c); }
        if(stage==5 && ticks==50) {
            String name="hunteralert-legacy.png";
            ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,t -> LoggerFactory.getLogger("HunterAlertSmoke").info("SCREENSHOT {}",name));
            var c=mod.store.editableCopy(); c.logView=true; mod.store.save(c);
        }
        if(ticks==35) {
            String name="hunteralert-smoke-"+stage+".png";
            ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,t -> LoggerFactory.getLogger("HunterAlertSmoke").info("SCREENSHOT {}",name));
            LoggerFactory.getLogger("HunterAlertSmoke").info("RENDERED stage={} screen={}",stage,client.currentScreen.getClass().getSimpleName());
        }
        if(ticks<60) return;
        ticks=0; stage++;
        switch(stage) {
            case 1 -> client.setScreen(new SettingsScreen(title,mod));
            case 2 -> {
                mod.suggestionSnapshot=new Snapshot(Feed.unavailable(),List.of(),Map.of("candidatealpha",new Profile(new Identity(null,"CandidateAlpha"),"","",1)),Map.of("candidatealpha",26,"candidatebravo",10,"belowthreshold",9),System.currentTimeMillis());
                client.setScreen(new HunterManagerScreen(title,mod));
            }
            case 3 -> client.setScreen(new HistoryScreen(title,mod));
            case 4 -> client.setScreen(new HudEditorScreen(title,mod));
            case 5 -> {
                long now=System.currentTimeMillis();
                var p=new MapPlayer(new Identity(null,"ExampleHunter"),new Position("terra",-582,-582),now);
                mod.view.set(new View(new Exposure(Visibility.EXPOSED,now-134_000,now-134_000,0,now-134_000,0,73),List.of(new Assessment(p,Kind.KNOWN,true,true,false,23,824,"NW",Radius.WARNING,Trend.APPROACHING,-82,10_000,false,now)),List.of(),true,true,List.of(new ThreatMarker("clue",p.identity(),new Position("terra",100,200),MarkerType.AFFILIATION,"ExampleHunter -> NearTown | affiliation clue",23,now,now+300_000))));
                mod.alertLog.clear();
                for(int i=1;i<=3;i++) {
                    long at=now-(3-i)*3000;
                    var progress=new Approach(p.identity(),i,1004-i*60,at,true,List.of(new Position("terra",-720,-720),p.position()));
                    var events=i==3 ? List.of(new Alert(at,"player:"+p.identity().key()+":approaching","POTENTIAL HUNTER APPROACHING","ExampleHunter approaching",2)) : List.<Alert>of();
                    mod.alertLog.accept(new View(mod.view.get().exposure(),mod.view.get().nearby(),events,true,true,mod.view.get().markers(),List.of(progress)),3);
                }
                if(mod.alertLog.recent(now,60,8).size()!=3) throw new AssertionError("Compact instance log mismatch");
                mod.alertLog.accept(new View(Exposure.unknown(),List.of(),List.of(new Alert(now,"fixture:affiliation","AFFILIATION WARNING","hungrybalong6 changed nation to NearbyNation. Their town is near you; possible incoming travel.",2)),true,true),3);
                mod.flashUntil=now+60000;
                var smokeConfig=mod.store.editableCopy(); smokeConfig.logView=true; mod.store.save(smokeConfig);
                HunterHud hud=new HunterHud(mod); mod.active=true; mod.raining=true; hud.refresh(now);
                client.setScreen(new Screen(Text.literal("Synthetic HUD smoke fixture")) {
                    @Override public void render(DrawContext ctx,int mx,int my,float delta) { super.render(ctx,mx,my,delta);
                        var obstacle=new net.earthmc.hunteralert.api.HudObstacles.Rect(4,4,160,180);
                        net.earthmc.hunteralert.api.HudObstacles.publish("smoke-map",obstacle);
                        ctx.fill(4,4,164,184,0xFF284A38);
                        net.earthmc.hunteralert.api.HudObstacles.begin();
                        ctx.drawText(client.textRenderer,"Minimap + town info fixture",10,160,0xFFFFFFFF,false);
                        net.earthmc.hunteralert.api.HudObstacles.end();
                        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaerominimap") && net.earthmc.hunteralert.api.HudObstacles.snapshot().size()<2) throw new AssertionError("HUD text capture hook did not run");
                        hud.render(ctx); }
                });
            }
            case 6 -> {
                long now=System.currentTimeMillis();
                var id=new Identity(null,"MapHunter");
                var fixtures=List.of(new ThreatMarker("live",id,new Position("terra",25,100),MarkerType.LIVE,"Public map",40,now,now+60_000),new ThreatMarker("last",new Identity(null,"HiddenHunter"),new Position("terra",-30,100),MarkerType.LAST_SEEN,"Last public position",25,now-20_000,now+60_000));
                client.setScreen(new Screen(Text.literal("Public map marker fixture")) {
                    @Override public void render(DrawContext ctx,int mx,int my,float delta) {
                        super.render(ctx,mx,my,delta);
                        ctx.drawText(client.textRenderer,net.earthmc.hunteralert.platform.mixin.OptionalMixinPlugin.addon() ? "EarthMC map overlay fixture" : "Public map X/Z bearings - height unknown",8,16,0xFFFFFFFF,false);
                        if(net.earthmc.hunteralert.platform.mixin.OptionalMixinPlugin.addon()) {
                            net.townymap.TownyMapMod.renderWorldMapLatePass(ctx,0,0,.7,ctx.getScaledWindowWidth(),ctx.getScaledWindowHeight());
                            var mapFixtures=new java.util.ArrayList<>(fixtures);
                            mapFixtures.add(new ThreatMarker("town",id,new Position("terra",100,-60),MarkerType.AFFILIATION,"MapHunter -> NearbyTown",40,now,now+60_000));
                            PathTraceRenderer.map(ctx,List.of(new Approach(id,3,103,now,true,List.of(new Position("terra",-200,-80),new Position("terra",-120,-40),new Position("terra",-40,20),new Position("terra",25,100)))),"terra",0,0,.7,ctx.getScaledWindowWidth(),ctx.getScaledWindowHeight(),System.currentTimeMillis(),15);
                            AddonMapOverlay.draw(ctx,mapFixtures,"terra",0,0,.7,ctx.getScaledWindowWidth(),ctx.getScaledWindowHeight(),System.currentTimeMillis());
                         } else {
                            PathTraceRenderer.world(ctx,List.of(new Approach(id,3,103,now,true,List.of(new Position("terra",-100,160),new Position("terra",-60,130),new Position("terra",25,100)))),new Position("terra",0,0),0,0,70,System.currentTimeMillis(),15);
                            WorldThreatMarkers.draw(ctx,fixtures,new Position("terra",0,0),0,0,70);
                        }
                    }
                });
            }
            case 7 -> {
                if(net.earthmc.hunteralert.platform.mixin.OptionalMixinPlugin.addon()) AddonSettingsProbe.open(client,title);
                else client.setScreen(title);
            }
            case 8 -> {
                if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) {
                    ModMenuProbe.verify(client,title);
                } else client.setScreen(title);
            }
            case 9 -> {
                if(!feedTest.isDone()) { stage=8; ticks=59; return; }
                feedTest.join();
                mod.active=false; LoggerFactory.getLogger("HunterAlertSmoke").info("SMOKE_PASS: title, settings, management, history, compact instance log, observed path and HUD rendered"); client.scheduleStop();
            }
            default -> { }
        }
    }
}
