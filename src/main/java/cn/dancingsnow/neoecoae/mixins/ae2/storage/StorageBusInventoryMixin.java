package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import cn.dancingsnow.neoecoae.impl.storage.transfer.StorageBusTransferFilter;
import org.spongepowered.asm.mixin.Mixin;

/** Tags the exact inventory mounted by storage buses, preserving their access and priority rules. */
@Mixin(value = appeng.parts.storagebus.StorageBusPart.StorageBusInventory.class, remap = false)
public abstract class StorageBusInventoryMixin implements StorageBusTransferFilter.StorageBusInventory {}
