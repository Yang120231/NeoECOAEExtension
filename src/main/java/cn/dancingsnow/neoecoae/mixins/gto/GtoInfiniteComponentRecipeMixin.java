package cn.dancingsnow.neoecoae.mixins.gto;

import cn.dancingsnow.neoecoae.compat.gto.ECOGtoInfiniteComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.api.recipe.GTRecipeBuilder", remap = false)
public abstract class GtoInfiniteComponentRecipeMixin {
    @Inject(method = "inputItems(Lnet/minecraft/world/item/ItemStack;)Lcom/gregtechceu/gtceu/api/recipe/GTRecipeBuilder;",
            at = @At("HEAD"), cancellable = true)
    private void neoecoae$componentAlternatives(ItemStack input, CallbackInfoReturnable<Object> callback) {
        if (!input.hasTag() && ECOGtoInfiniteComponents.isComponent(input)) {
            callback.setReturnValue(ECOGtoInfiniteComponents.addRecipeInput(this, input));
        }
    }
}
