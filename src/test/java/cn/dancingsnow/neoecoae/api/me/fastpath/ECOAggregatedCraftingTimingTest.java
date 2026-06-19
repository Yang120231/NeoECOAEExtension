package cn.dancingsnow.neoecoae.api.me.fastpath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOAggregatedCraftingTimingTest {
    @Test
    void gtTimingMatchesSuperMolecularAssemblerEnergyFormula() {
        long totalOutputAmount = 65_536L;
        long voltage = 2_048L;

        assertEquals(32L, ECOAggregatedCraftingTiming.calculateGtTicks(totalOutputAmount, voltage, 0));
        assertEquals(2_048L, ECOAggregatedCraftingTiming.calculateGtEnergyPerTick(totalOutputAmount, voltage, 0));
        assertEquals(32L, ECOAggregatedCraftingTiming.calculateGtTicks(totalOutputAmount, voltage, 4));
        assertEquals(2_048L, ECOAggregatedCraftingTiming.calculateGtEnergyPerTick(totalOutputAmount, voltage, 4));
    }

    @Test
    void gtTimingUsesMinimumDurationBelowFullVoltageLoad() {
        long totalOutputAmount = 19L;
        long voltage = 2_048L;

        assertEquals(20L, ECOAggregatedCraftingTiming.calculateGtTicks(totalOutputAmount, voltage, 0));
        assertEquals(1L, ECOAggregatedCraftingTiming.calculateGtEnergyPerTick(totalOutputAmount, voltage, 0));
    }

    @Test
    void aeTimingStillUsesExistingParallelAndPowerMultiplierFormula() {
        assertEquals(150L, ECOAggregatedCraftingTiming.calculateAeTicks(30L, 2, 10));
        assertEquals(24L, ECOAggregatedCraftingTiming.calculateAeEnergyPerTick(3, 4, 2));
    }
}
