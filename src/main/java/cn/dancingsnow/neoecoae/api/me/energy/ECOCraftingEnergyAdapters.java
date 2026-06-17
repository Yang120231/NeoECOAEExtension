package cn.dancingsnow.neoecoae.api.me.energy;

public final class ECOCraftingEnergyAdapters {
    public static final ECOCraftingEnergyAdapter NONE = new ECOCraftingEnergyAdapter() {
        @Override
        public ECOCraftingEnergySnapshot snapshot(ECOCraftingEnergyRequest request) {
            return ECOCraftingEnergySnapshot.unavailable(
                    request.mode(), Math.max(0L, request.requiredEnergy()), "none");
        }

        @Override
        public ECOCraftingEnergyResult drain(ECOCraftingEnergyRequest request) {
            return simulate(request);
        }
    };

    private ECOCraftingEnergyAdapters() {}
}
