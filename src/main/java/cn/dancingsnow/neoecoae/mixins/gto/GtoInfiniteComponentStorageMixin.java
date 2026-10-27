package cn.dancingsnow.neoecoae.mixins.gto;

import cn.dancingsnow.neoecoae.compat.gto.ECOGtoInfiniteComponents;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.api.transfer.key.StackInventory", remap = false)
public abstract class GtoInfiniteComponentStorageMixin {
    @ModifyVariable(method = "setFilter", at = @At("HEAD"), argsOnly = true)
    private Predicate<ItemStack> neoecoae$componentAlternatives(Predicate<ItemStack> original) {
        return ECOGtoInfiniteComponents.storageFilter(original);
    }
}
