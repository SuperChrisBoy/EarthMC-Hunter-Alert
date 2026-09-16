package net.earthmc.hunteralert.platform.mixin;

import net.earthmc.hunteralert.api.EmcMapAddonBridge;
import net.earthmc.hunteralert.api.FeedValidation;
import net.earthmc.hunteralert.model.Models.*;
import net.townymap.api.SquaremapApiClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe existing worker requests; no extra HTTP, reflection, accessors or result replacement. */
@Mixin(value = SquaremapApiClient.class, remap = false)
public abstract class PlayerFeedMixin {
    @Unique private final ThreadLocal<Boolean> hunteralert$health = new ThreadLocal<>();
    @Unique private final ThreadLocal<Long> hunteralert$generation = new ThreadLocal<>();
    @Unique private final ThreadLocal<Boolean> hunteralert$notModified = new ThreadLocal<>();
    @Unique private Feed hunteralert$lastGood = Feed.unavailable();
    @Unique private long hunteralert$lastGeneration = -1;
    @Inject(method = "fetchPlayers", at = @At("HEAD"))
    private void hunteralert$begin(CallbackInfo ci) {
        long generation=EmcMapAddonBridge.generation();
        if(generation!=hunteralert$lastGeneration) { hunteralert$lastGood=Feed.unavailable(); hunteralert$lastGeneration=generation; }
        hunteralert$generation.set(generation); hunteralert$health.set(false); hunteralert$notModified.set(false);
    }
    @Inject(method = "get", at = @At("RETURN"))
    private void hunteralert$response(String url, CallbackInfoReturnable<String> cir) {
        if (hunteralert$health.get() == null) return;
        String body = cir.getReturnValue();
        boolean unchanged="__not_modified__".equals(body);
        hunteralert$notModified.set(unchanged);
        hunteralert$health.set(unchanged ? hunteralert$lastGood.healthy() : FeedValidation.valid(body));
    }
    @Inject(method = "fetchPlayers", at = @At("RETURN"))
    private void hunteralert$complete(CallbackInfo ci) {
        boolean healthy = Boolean.TRUE.equals(hunteralert$health.get());
        boolean unchanged = Boolean.TRUE.equals(hunteralert$notModified.get());
        long generation=hunteralert$generation.get();
        hunteralert$health.remove();
        hunteralert$generation.remove(); hunteralert$notModified.remove();
        long now = System.currentTimeMillis();
        var players = healthy && unchanged ? hunteralert$lastGood.players().stream().map(p -> new MapPlayer(p.identity(),p.position(),now)).toList() : healthy ? ((SquaremapApiClient)(Object)this).getAllPlayers().stream()
            .filter(p -> p.name() != null && !p.name().equals("?"))
            .map(p -> new MapPlayer(Identity.of(p.uuid(),p.name()), new Position(p.world(),p.x(),p.z()),now)).toList() : EmcMapAddonBridge.latest().players();
        Feed result=new Feed(now,0,healthy,players);
        if(healthy) hunteralert$lastGood=result;
        EmcMapAddonBridge.publish(result,generation);
    }
}
