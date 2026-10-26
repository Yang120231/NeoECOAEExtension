package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.storage.MEStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.me.storage.NetworkStorage$MountOperation", remap = false)
public interface NetworkStorageMountAccessor {
    @Accessor("priority") int neoecoae$priority();
    @Accessor("storage") MEStorage neoecoae$storage();
}
