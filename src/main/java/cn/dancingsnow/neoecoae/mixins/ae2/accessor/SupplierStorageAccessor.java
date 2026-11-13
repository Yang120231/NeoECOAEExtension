package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.api.storage.MEStorage;
import appeng.api.storage.SupplierStorage;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SupplierStorage.class, remap = false)
public interface SupplierStorageAccessor {
    @Accessor(value = "supplier", remap = false)
    Supplier<MEStorage> neoecoae$getSupplier();

}
