package cn.dancingsnow.neoecoae.api.me.fastpath;

public final class ECOAggregatedCraftingTiming {
    public static final int MINIMUM_DURATION_TICKS = 20;
    private static final long SPEED_MULTIPLIER_PER_OVERCLOCK = 2L;

    private ECOAggregatedCraftingTiming() {}

    public static long calculateAeTicks(long craftCount, int effectiveParallel, int theoreticalCraftTicks) {
        if (craftCount <= 0L || effectiveParallel <= 0 || theoreticalCraftTicks <= 0) {
            return 0L;
        }
        return saturatedMultiply(ceilDiv(craftCount, effectiveParallel), theoreticalCraftTicks);
    }

    public static long calculateAeEnergyPerTick(
            int effectiveParallel, int progressPerTick, int craftingPowerMultiplier) {
        long energy = saturatedMultiply(Math.max(1, effectiveParallel), Math.max(1, progressPerTick));
        return saturatedMultiply(energy, Math.max(1, craftingPowerMultiplier));
    }

    public static long calculateGtTicks(long totalOutputAmount, long baseVoltage, int effectiveOverclock) {
        long processingRate = effectiveProcessingRate(baseVoltage, effectiveOverclock);
        if (totalOutputAmount <= 0L || processingRate <= 0L) {
            return 0L;
        }
        return Math.max(MINIMUM_DURATION_TICKS, totalOutputAmount / processingRate);
    }

    public static long calculateGtEnergyPerTick(long totalOutputAmount, long baseVoltage, int effectiveOverclock) {
        long voltage = Math.max(0L, baseVoltage);
        if (totalOutputAmount <= 0L || voltage <= 0L) {
            return 0L;
        }
        if (totalOutputAmount / voltage >= MINIMUM_DURATION_TICKS) {
            return voltage;
        }
        return Math.max(1L, totalOutputAmount / MINIMUM_DURATION_TICKS);
    }

    private static long effectiveProcessingRate(long baseVoltage, int effectiveOverclock) {
        long rate = Math.max(1L, baseVoltage);
        for (int i = 0; i < Math.max(0, effectiveOverclock); i++) {
            rate = saturatedMultiply(rate, SPEED_MULTIPLIER_PER_OVERCLOCK);
            if (rate == Long.MAX_VALUE) {
                break;
            }
        }
        return rate;
    }

    public static double progress(long totalTicks, long remainingTicks) {
        if (totalTicks <= 0L) {
            return 0.0D;
        }
        long clampedRemaining = Math.max(0L, Math.min(totalTicks, remainingTicks));
        return Math.max(0.0D, Math.min(1.0D, (totalTicks - clampedRemaining) / (double) totalTicks));
    }

    public static long rescaleRemaining(long oldTotalTicks, long oldRemainingTicks, long newTotalTicks) {
        if (newTotalTicks <= 0L) {
            return 0L;
        }
        if (oldTotalTicks <= 0L || oldRemainingTicks <= 0L) {
            return newTotalTicks;
        }
        double remainingRatio = oldRemainingTicks / (double) oldTotalTicks;
        return Math.max(1L, Math.min(newTotalTicks, (long) Math.ceil(newTotalTicks * remainingRatio)));
    }

    public static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0L && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    public static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static long ceilDiv(long dividend, long divisor) {
        return 1L + (dividend - 1L) / divisor;
    }
}
