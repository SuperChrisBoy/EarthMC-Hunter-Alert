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
        if(ticks==35) {
            String name="hunteralert-smoke-"+stage+".png";
            ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,t -> LoggerFactory.getLogger("HunterAlertSmoke").info("SCREENSHOT {}",name));
            LoggerFactory.getLogger("HunterAlertSmoke").info("RENDERED stage={} screen={}",stage,client.currentScreen.getClass().getSimpleName());
        }
        if(ticks<60) return;
        ticks=0; stage++;
        switch(stage) {
            case 1 -> client.setScreen(new SettingsScreen(title,mod));
            case 2 -> client.setScreen(new HunterManagerScreen(title,mod));
            case 3 -> client.setScreen(new HistoryScreen(title,mod));
            case 4 -> client.setScreen(new HudEditorScreen(title,mod));
            case 5 -> {
                long now=System.currentTimeMillis();
                var p=new MapPlayer(new Identity(null,"ExampleHunter"),new Position("terra",-582,-582),now);
                mod.view.set(new View(new Exposure(Visibility.EXPOSED,now-134_000,now-134_000,0,now-134_000,0,73),List.of(new Assessment(p,Kind.KNOWN,true,true,false,23,824,"NW",Radius.WARNING,Trend.APPROACHING,-82,10_000,false,now)),List.of(),true,true,List.of(new ThreatMarker("clue",p.identity(),new Position("terra",100,200),MarkerType.AFFILIATION,"ExampleHunter -> NearTown | affiliation clue",23,now,now+300_000))));
                HunterHud hud=new HunterHud(mod); mod.active=true; hud.refresh(now);
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
                            AddonMapOverlay.draw(ctx,mapFixtures,"terra",0,0,.7,ctx.getScaledWindowWidth(),ctx.getScaledWindowHeight(),System.currentTimeMillis());
                        } else WorldThreatMarkers.draw(ctx,fixtures,new Position("terra",0,0),0,0,70);
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
                mod.active=false; LoggerFactory.getLogger("HunterAlertSmoke").info("SMOKE_PASS: title, settings, management, history, HUD editor and HUD rendered"); client.scheduleStop();
            }
            default -> { }
        }
    }
}
