package cn.dancingsnow.neoecoae.api.me.fastpath;

public record ECOBatchEnergyProfile(
        ECOCraftingEnergyMode mode, ECOCraftingEnergyStatus status, long energyPerTick, long totalTicks) {
    public ECOBatchEnergyProfile {
        mode = mode == null ? ECOCraftingEnergyMode.AE : mode;
        status = status == null ? ECOCraftingEnergyStatus.READY : status;
        energyPerTick = Math.max(0L, energyPerTick);
        totalTicks = Math.max(1L, totalTicks);
    }
}
