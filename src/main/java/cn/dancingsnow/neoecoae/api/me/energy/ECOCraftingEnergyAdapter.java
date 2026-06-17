package cn.dancingsnow.neoecoae.api.me.energy;

public interface ECOCraftingEnergyAdapter {
    ECOCraftingEnergySnapshot snapshot(ECOCraftingEnergyRequest request);

    default ECOCraftingEnergyResult simulate(ECOCraftingEnergyRequest request) {
        return snapshot(request).asResult(false);
    }

    ECOCraftingEnergyResult drain(ECOCraftingEnergyRequest request);
}
