package cn.dancingsnow.neoecoae.mixins.client.emi;

import cn.dancingsnow.neoecoae.integration.emi.ECOEmiPlugin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.registry.EmiPluginContainer;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Retains GTO's selected plugins and adds ECO after its discovery overwrite. */
@Mixin(value = EmiForgePlatform.class, remap = false, priority = 500)
public abstract class GtoEmiPluginMixin {
    @ModifyReturnValue(method = "getPlugins", at = @At("RETURN"))
    private static List<EmiPluginContainer> neoecoae$includeWorkstations(List<EmiPluginContainer> plugins) {
        return ECOEmiPlugin.includeIn(plugins);
    }
}
