package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.api.EmcMapAddonBridge;
import net.earthmc.hunteralert.config.*;
import net.earthmc.hunteralert.detection.ThreatEngine;
import net.earthmc.hunteralert.model.Models.*;
import net.earthmc.hunteralert.platform.gui.SettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.earthmc.hunteralert.data.*;
import net.earthmc.hunteralert.platform.mixin.OptionalMixinPlugin;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class HunterAlertClient implements ClientModInitializer {
    public static HunterAlertClient INSTANCE;
    public LocalStore store;
    public final AtomicReference<View> view = new AtomicReference<>(View.empty());
    public volatile boolean active;
    public long flashUntil;
    private final ThreatEngine engine=new ThreatEngine();
    private final EarthMcDataProvider provider=OptionalMixinPlugin.addon() ? new EmcMapAddonDataProvider() : new DirectEarthMcDataProvider();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r,"HunterAlert-detection"); t.setDaemon(true); return t; });
    private final AtomicBoolean busy=new AtomicBoolean();
    private long lastTick;
    private int session;
    private Object connection;
    private final HunterHud hud=new HunterHud(this);
    @Override public void onInitializeClient() {
        INSTANCE=this;
        store=new LocalStore(FabricLoader.getInstance().getConfigDir().resolve("hunteralert"));
        var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("hunteralert","controls"));
        KeyMapping settings=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hunteralert.settings",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_H,category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(Boolean.getBoolean("hunteralert.smoke") && FabricLoader.getInstance().isDevelopmentEnvironment()) { SmokeProbe.tick(client,this); return; }
            while(settings.consumeClick()) if(store.ready()) client.setScreen(new SettingsScreen(client.screen,this));
            tick(client);
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("hunteralert","hud"),(context,counter) -> hud.render(context));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { worker.shutdownNow(); provider.close(); store.close(); });
        LoggerFactory.getLogger("HunterAlert").info("Initialized EarthMC data provider");
    }
    public static boolean earthHost(String address) {
        return net.earthmc.hunteralert.data.ServerIdentity.isEarthMc(address);
    }
    private void tick(Minecraft client) {
        if(!store.ready()) return;
        var config=store.config();
        boolean connected=config.enabled && client.player != null && client.level != null && client.getCurrentServer() != null
            && earthHost(client.getCurrentServer().ip);
        Object currentConnection=client.getConnection();
        if(connected != active || connection != currentConnection) {
            active=connected; connection=currentConnection; session++;
            provider.reset(); EmcMapAddonBridge.reset(); view.set(View.empty()); worker.execute(engine::reset);
        }
        if(!active) return;
        long now=System.currentTimeMillis();
        hud.refresh(now);
        if(now-lastTick < 250 || !busy.compareAndSet(false,true)) return;
        lastTick=now;
        Identity self=new Identity(client.player.getUUID(),client.player.getName().getString());
        provider.localIdentity(self);
        try { provider.tick(now,config); }
        catch(RuntimeException error) { busy.set(false); LoggerFactory.getLogger("HunterAlert").warn("EarthMC data capture failed",error); return; }
        Position local=localMapPosition(client);
        int generation=session;
        worker.execute(() -> {
            try {
                View next=engine.update(provider.snapshot(),self,local,now,config);
                client.execute(() -> {
                    if(generation != session || !active) return;
                    view.set(next); store.append(next.events());
                    upgradeIdentities(next);
                    for(Alert alert:next.events()) notify(client,alert,config);
                });
            } catch(Exception error) { LoggerFactory.getLogger("HunterAlert").warn("Snapshot evaluation failed",error); }
            finally { busy.set(false); }
        });
    }
    private void upgradeIdentities(View next) {
        var current=store.config();
        boolean upgrade=current.tracked.stream().anyMatch(t -> t.identity().uuid()==null && next.nearby().stream().anyMatch(a -> a.player().identity().uuid()!=null && t.identity().matches(a.player().identity())));
        if(!upgrade) return;
        var copy=store.editableCopy();
        copy.tracked.replaceAll(t -> {
            if(t.identity().uuid()!=null) return t;
            var identity=next.nearby().stream().map(a -> a.player().identity()).filter(id -> id.uuid()!=null && t.identity().matches(id)).findFirst();
            return identity.map(id -> new HunterAlertConfig.Tracked(id,t.kind(),t.note(),t.enabled())).orElse(t);
        });
        store.save(copy);
    }
    private Position localMapPosition(Minecraft client) {
        // These are local-player coordinates only; never entity distances to other players.
        if(client.level == null || client.player == null) return null;
        String world=provider.mapWorld(client.level.dimension().identifier().toString());
        if(world == null || world.isBlank() || client.level == null || client.player == null) return null;
        if(client.level.dimension().equals(Level.END)) return null;
        double scale=client.level.dimension().equals(Level.NETHER) ? 8 : 1;
        return new Position(world,client.player.getX()*scale,client.player.getZ()*scale);
    }
    private void notify(Minecraft client,Alert alert,HunterAlertConfig config) {
        if(config.debug) LoggerFactory.getLogger("HunterAlert").info("{}: {}",alert.key(),alert.message());
        if(config.flash) flashUntil=System.currentTimeMillis()+1800;
        if(config.chat && client.player != null) client.gui.getChat().addClientSystemMessage(Component.literal("[Hunter Alert] "+alert.title()+": "+alert.message()));
        if(config.toast) SystemToast.add(client.getToastManager(),SystemToast.SystemToastId.PERIODIC_NOTIFICATION,Component.literal(alert.title()),Component.literal(alert.message()));
        if(config.sound) client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(Identifier.parse(config.soundId)),1,config.soundVolume));
    }
}
