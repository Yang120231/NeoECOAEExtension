package cn.dancingsnow.neoecoae.api.me.energy;

import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingEnergyMode;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record ECOCraftingEnergyRequest(
        ECOCraftingEnergyMode mode, long requiredEnergy, long maxRate, @Nullable UUID owner) {
    public ECOCraftingEnergyRequest {
        mode = mode == null ? ECOCraftingEnergyMode.AE : mode;
        requiredEnergy = Math.max(0L, requiredEnergy);
        maxRate = Math.max(0L, maxRate);
    }
}
