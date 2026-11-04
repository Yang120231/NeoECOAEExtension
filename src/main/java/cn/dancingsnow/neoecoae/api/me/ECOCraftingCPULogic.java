package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Shared contract for ECO execution logic and its menu/persistence adapters. */
public abstract class ECOCraftingCPULogic {
    protected ExecutingCraftingJob job;
    protected abstract void finishJob(boolean success);
    public abstract long insert(AEKey what, long amount, Actionable type);
    public abstract int executeCrafting(int maxPatterns, CraftingService service, IEnergyService energy, Level level);

    public final void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        readNativePersistentData(data);
    }
    public final void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        writeNativePersistentData(data);
    }
    protected abstract void readNativePersistentData(CompoundTag data);
    protected abstract void writeNativePersistentData(CompoundTag data);
}
