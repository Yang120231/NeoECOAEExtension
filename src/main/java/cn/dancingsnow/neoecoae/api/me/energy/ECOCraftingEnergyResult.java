package cn.dancingsnow.neoecoae.api.me.energy;

import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingEnergyMode;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingEnergyStatus;

public record ECOCraftingEnergyResult(
        ECOCraftingEnergyMode mode,
        ECOCraftingEnergyStatus status,
        boolean available,
        boolean drained,
        long requiredEnergy,
        long availableEnergy,
        long maxRate,
        String source) {
    public ECOCraftingEnergyResult {
        mode = mode == null ? ECOCraftingEnergyMode.AE : mode;
        status = status == null ? ECOCraftingEnergyStatus.READY : status;
        requiredEnergy = Math.max(0L, requiredEnergy);
        availableEnergy = Math.max(0L, availableEnergy);
        maxRate = Math.max(0L, maxRate);
        source = source == null ? "" : source;
    }
}
