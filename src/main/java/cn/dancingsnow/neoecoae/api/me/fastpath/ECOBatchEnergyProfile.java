package cn.dancingsnow.neoecoae.api.me.fastpath;

public record ECOBatchEnergyProfile(
        ECOCraftingEnergyMode mode,
        ECOCraftingEnergyStatus status,
        long energyPerTick,
        long totalTicks,
        long requiredEnergy,
        long availableEnergy,
        long maxEnergyRate,
        String energySource) {
    public ECOBatchEnergyProfile {
        mode = mode == null ? ECOCraftingEnergyMode.AE : mode;
        status = status == null ? ECOCraftingEnergyStatus.READY : status;
        energyPerTick = Math.max(0L, energyPerTick);
        totalTicks = Math.max(1L, totalTicks);
        requiredEnergy = Math.max(0L, requiredEnergy);
        availableEnergy = Math.max(0L, availableEnergy);
        maxEnergyRate = Math.max(0L, maxEnergyRate);
        energySource = energySource == null ? "" : energySource;
    }

    public ECOBatchEnergyProfile(
            ECOCraftingEnergyMode mode, ECOCraftingEnergyStatus status, long energyPerTick, long totalTicks) {
        this(mode, status, energyPerTick, totalTicks, 0L, 0L, 0L, "");
    }
}
