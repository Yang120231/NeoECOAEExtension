package cn.dancingsnow.neoecoae.compat.gtl;

import cn.dancingsnow.neoecoae.api.me.fastpath.ECOAggregatedCraftingTiming;
import cn.dancingsnow.neoecoae.config.NEConfig;

public final class GTLConfigCompat {
    private GTLConfigCompat() {}

    public static int getBatchProcessingMaxDurationTicks() {
        return Math.max(
                ECOAggregatedCraftingTiming.MINIMUM_DURATION_TICKS, NEConfig.getBatchProcessingMaxDurationTicks());
    }

    public static long limitBatchProcessingDurationTicks(long requestedTicks) {
        return requestedTicks <= 0L ? 0L : Math.min(requestedTicks, getBatchProcessingMaxDurationTicks());
    }
}
