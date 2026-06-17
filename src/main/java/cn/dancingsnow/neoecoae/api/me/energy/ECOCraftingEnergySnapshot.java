package cn.dancingsnow.neoecoae.api.me.energy;

import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingEnergyMode;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingEnergyStatus;

public record ECOCraftingEnergySnapshot(
        ECOCraftingEnergyMode mode,
        ECOCraftingEnergyStatus status,
        boolean available,
        long requiredEnergy,
        long availableEnergy,
        long maxRate,
        String source) {
    public ECOCraftingEnergySnapshot {
        mode = mode == null ? ECOCraftingEnergyMode.AE : mode;
        status = status == null ? ECOCraftingEnergyStatus.READY : status;
        requiredEnergy = Math.max(0L, requiredEnergy);
        availableEnergy = Math.max(0L, availableEnergy);
        maxRate = Math.max(0L, maxRate);
        source = source == null ? "" : source;
    }

    public static ECOCraftingEnergySnapshot unavailable(
            ECOCraftingEnergyMode mode, long requiredEnergy, String source) {
        return new ECOCraftingEnergySnapshot(
                mode, ECOCraftingEnergyStatus.UNAVAILABLE, false, requiredEnergy, 0L, 0L, source);
    }

    public ECOCraftingEnergyResult asResult(boolean drained) {
        return new ECOCraftingEnergyResult(
                mode, status, available, drained && available, requiredEnergy, availableEnergy, maxRate, source);
    }
}
