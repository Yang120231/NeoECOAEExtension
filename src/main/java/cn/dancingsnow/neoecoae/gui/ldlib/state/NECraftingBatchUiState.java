package cn.dancingsnow.neoecoae.gui.ldlib.state;

import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingEnergyMode;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingEnergyStatus;
import net.minecraft.world.item.ItemStack;

public record NECraftingBatchUiState(
        String id,
        ItemStack primaryOutput,
        long craftCount,
        long outputAmount,
        long totalTicks,
        long remainingTicks,
        long energyPerTick,
        ECOCraftingEnergyMode energyMode,
        ECOCraftingEnergyStatus energyStatus,
        boolean completed,
        boolean canceled) {
    public NECraftingBatchUiState {
        id = id == null ? "" : id;
        primaryOutput = primaryOutput == null ? ItemStack.EMPTY : primaryOutput;
        craftCount = Math.max(0L, craftCount);
        outputAmount = Math.max(0L, outputAmount);
        totalTicks = Math.max(0L, totalTicks);
        remainingTicks = Math.max(0L, remainingTicks);
        energyPerTick = Math.max(0L, energyPerTick);
        energyMode = energyMode == null ? ECOCraftingEnergyMode.AE : energyMode;
        energyStatus = energyStatus == null ? ECOCraftingEnergyStatus.READY : energyStatus;
    }
}
