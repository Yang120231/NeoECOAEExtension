package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountDisplay;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Slot labels and stored-amount tooltips both end in this formatter in GTO's terminal. */
@Mixin(value = AEKey.class, remap = false)
public abstract class AEKeyExactAmountMixin {
    @ModifyReturnValue(method = "formatAmount", at = @At("RETURN"))
    private String neoecoae$formatExactAmount(String original, long amount, AmountFormat format) {
        return ExactAmountDisplay.format((AEKey) (Object) this, amount, format, original);
    }
}
