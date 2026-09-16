package net.earthmc.hunteralert.platform.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.extensibility.*;
import org.objectweb.asm.tree.ClassNode;
import java.util.*;

public final class OptionalMixinPlugin implements IMixinConfigPlugin {
    public static boolean addon() { return FabricLoader.getInstance().getModContainer("townymapaddon").map(m -> m.getMetadata().getVersion().getFriendlyString().equals("1.4.5")).orElse(false); }
    public void onLoad(String p) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target,String mixin) { return mixin.endsWith("PlayerFeedMixin") || mixin.endsWith("EarthApiMixin") || mixin.endsWith("AddonMapMixin") || mixin.endsWith("AddonSettingsMixin") ? addon() : FabricLoader.getInstance().isModLoaded("xaerominimap"); }
    public void acceptTargets(Set<String> mine,Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String name,ClassNode node,String mixin,IMixinInfo info) {}
    public void postApply(String name,ClassNode node,String mixin,IMixinInfo info) {}
}
