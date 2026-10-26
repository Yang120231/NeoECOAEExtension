package cn.dancingsnow.neoecoae.mixins.client.emi;

import cn.dancingsnow.neoecoae.integration.emi.NeoECOAEEmiPlugin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.emi.emi.registry.EmiPluginContainer;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** GTO replaces annotation discovery with its own allowlist. Append only ECO's native plugin. */
@Pseudo
@Mixin(targets = "dev.emi.emi.forge.EmiForgePlatform", remap = false, priority = 500)
public abstract class GTOEmiPluginsMixin {
    @ModifyReturnValue(method = "getPlugins", at = @At("RETURN"))
    private static List<EmiPluginContainer> neoecoae$includeOwnPlugin(List<EmiPluginContainer> plugins) {
        for (var entry : plugins) if (entry.plugin() instanceof NeoECOAEEmiPlugin) return plugins;
        var result = new ArrayList<>(plugins);
        result.add(new EmiPluginContainer(new NeoECOAEEmiPlugin(), "neoecoae"));
        return List.copyOf(result);
    }
}
