package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.compat.gto.ECOGtoCraftingCpuLogic;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wrap the GTO CPU installed by its constructor injection, preserving GTO's execution engine. */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 500)
public abstract class Ae2CraftingCpuFastPathMixin {
    @Shadow public CraftingCpuLogic craftingLogic;

    @cn.dancingsnow.neoecoae.platform.mixin.ObserveGtoPattern(helper = "init",
        descriptor = "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
        at = @At("RETURN"))
    private void neoecoae$installGtoBridge(CallbackInfo ci) {
        if (craftingLogic != null && !(craftingLogic instanceof ECOGtoCraftingCpuLogic)) {
            craftingLogic = new ECOGtoCraftingCpuLogic((CraftingCPUCluster) (Object) this, craftingLogic);
        }
    }
}
