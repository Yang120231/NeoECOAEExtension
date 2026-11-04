package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.dancingsnow.neoecoae.compat.gto.ECOGtoProcessingDispatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GTO buffer classes may be protected; the AE2 emission contract retains its public name. */
@Mixin(value = AEProcessingPattern.class, remap = false)
public abstract class GtoPatternBufferInputMixin {
    @Inject(method = "pushInputsToExternalInventory", at = @At("HEAD"), cancellable = true)
    private void neoecoae$emitGtoBatch(KeyCounter[] inputs, IPatternDetails.PatternInputSink sink, CallbackInfo ci) {
        if (ECOGtoProcessingDispatch.emitBatch((IPatternDetails) this, inputs, sink)) ci.cancel();
    }
}
