package cn.dancingsnow.neoecoae.forge.mixin;

import cn.dancingsnow.neoecoae.compat.jade.NEJadePlugin;
import cn.dancingsnow.neoecoae.compat.jade.provider.ECOCraftingSystemProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.impl.WailaClientRegistration;
import snownee.jade.impl.WailaCommonRegistration;
import snownee.jade.util.CommonProxy;

@Pseudo
@Mixin(targets = "snownee.jade.Jade", remap = false)
public abstract class JadeLoadCompleteMixin120 {
    @Inject(
            method = "loadComplete",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lsnownee/jade/impl/WailaCommonRegistration;loadComplete()V",
                            shift = At.Shift.BEFORE,
                            remap = false),
            require = 0,
            remap = false)
    private static void neoecoae$registerJadePlugin(CallbackInfo ci) {
        NEJadePlugin plugin = new NEJadePlugin();
        if (!isCommonRegistered()) {
            plugin.register(WailaCommonRegistration.INSTANCE);
        }
        if (CommonProxy.isPhysicallyClient() && !isClientRegistered()) {
            plugin.registerClient(WailaClientRegistration.INSTANCE);
        }
    }

    private static boolean isCommonRegistered() {
        return WailaCommonRegistration.INSTANCE
                .blockDataProviders
                .getObjects()
                .containsValue(ECOCraftingSystemProvider.INSTANCE);
    }

    private static boolean isClientRegistered() {
        return WailaClientRegistration.INSTANCE
                .blockComponentProviders
                .getObjects()
                .containsValue(ECOCraftingSystemProvider.INSTANCE);
    }
}
