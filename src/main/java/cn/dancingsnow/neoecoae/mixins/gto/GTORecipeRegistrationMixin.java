package cn.dancingsnow.neoecoae.mixins.gto;

import cn.dancingsnow.neoecoae.compat.gto.ECOGtoMachineRecipes;
import com.gtocore.data.Data;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GTO 0.5.6 has no machine-recipe addon event; join its existing registration lifecycle. */
@Mixin(value = Data.class, remap = false)
public abstract class GTORecipeRegistrationMixin {
    @Inject(method = "commonInit", at = @At(value = "INVOKE",
        target = "Lcom/gtolib/api/recipe/RecipeBuilder;finish()V"))
    private static void neoecoae$registerMachineRecipes(CallbackInfo ci) {
        ECOGtoMachineRecipes.register();
    }
}
