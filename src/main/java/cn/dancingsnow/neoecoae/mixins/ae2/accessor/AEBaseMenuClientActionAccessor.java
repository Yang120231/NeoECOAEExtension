package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.menu.AEBaseMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AEBaseMenu.class, remap = false)
public interface AEBaseMenuClientActionAccessor {
    @Invoker("registerClientAction")
    void neoecoae$registerClientAction(String action, Runnable handler);

    @Invoker("sendClientAction")
    void neoecoae$sendClientAction(String action);
}
