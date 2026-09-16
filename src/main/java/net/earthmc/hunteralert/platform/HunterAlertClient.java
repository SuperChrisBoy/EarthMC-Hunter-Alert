package net.earthmc.hunteralert.platform;

import net.earthmc.hunteralert.api.EmcMapAddonBridge;
import net.earthmc.hunteralert.config.*;
import net.earthmc.hunteralert.detection.ThreatEngine;
import net.earthmc.hunteralert.model.Models.*;
import net.earthmc.hunteralert.platform.gui.SettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
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
        var category=KeyBinding.Category.create(Identifier.of("hunteralert","controls"));
        KeyBinding settings=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hunteralert.settings",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_H,category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(Boolean.getBoolean("hunteralert.smoke") && FabricLoader.getInstance().isDevelopmentEnvironment()) { SmokeProbe.tick(client,this); return; }
            while(settings.wasPressed()) if(store.ready()) client.setScreen(new SettingsScreen(client.currentScreen,this));
            tick(client);
        });
        HudElementRegistry.addLast(Identifier.of("hunteralert","hud"),(context,counter) -> { WorldThreatMarkers.render(context,this); hud.render(context); });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { worker.shutdownNow(); provider.close(); store.close(); });
        LoggerFactory.getLogger("HunterAlert").info("Initialized EarthMC data provider");
    }
    public static boolean earthHost(String address) {
        return net.earthmc.hunteralert.data.ServerIdentity.isEarthMc(address);
    }
    private void tick(MinecraftClient client) {
        if(!store.ready()) return;
        var config=store.config();
        boolean connected=config.enabled && client.player != null && client.world != null && client.getCurrentServerEntry() != null
            && earthHost(client.getCurrentServerEntry().address);
        Object currentConnection=client.getNetworkHandler();
        if(connected != active || connection != currentConnection) {
            active=connected; connection=currentConnection; session++;
            provider.reset(); EmcMapAddonBridge.reset(); view.set(View.empty()); worker.execute(engine::reset);
        }
        if(!active) return;
        long now=System.currentTimeMillis();
        hud.refresh(now);
        if(now-lastTick < 250 || !busy.compareAndSet(false,true)) return;
        lastTick=now;
        Identity self=new Identity(client.player.getUuid(),client.player.getName().getString());
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
    public Position localMapPosition(MinecraftClient client) {
        // These are local-player coordinates only; never entity distances to other players.
        if(client.world == null || client.player == null) return null;
        String world=provider.mapWorld(client.world.getRegistryKey().getValue().toString());
        if(world == null || world.isBlank()) return null;
        if(client.world.getRegistryKey().equals(World.END)) return null;
        double scale=client.world.getRegistryKey().equals(World.NETHER) ? 8 : 1;
        return new Position(world,client.player.getX()*scale,client.player.getZ()*scale);
    }
    private void notify(MinecraftClient client,Alert alert,HunterAlertConfig config) {
        if(config.debug) LoggerFactory.getLogger("HunterAlert").info("{}: {}",alert.key(),alert.message());
        if(config.flash) flashUntil=System.currentTimeMillis()+1800;
        if(config.chat && client.player != null) client.player.sendMessage(Text.literal("[Hunter Alert] "+alert.title()+": "+alert.message()),false);
        if(config.toast) SystemToast.add(client.getToastManager(),SystemToast.Type.PERIODIC_NOTIFICATION,Text.literal(alert.title()),Text.literal(alert.message()));
        var cue=net.earthmc.hunteralert.alert.AlertSounds.forAlert(alert,config);
        if(config.sound) client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(Identifier.of(cue.id())),cue.pitch(),config.soundVolume));
    }
}
