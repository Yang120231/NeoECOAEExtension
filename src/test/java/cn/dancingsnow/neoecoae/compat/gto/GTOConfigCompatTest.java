package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GTOConfigCompatTest {
    @Test
    void batchProcessingDurationFallsBackWhenGtoIsAbsent() {
        assertEquals(64L, GTOConfigCompat.limitBatchProcessingDurationTicks(64L));
        assertEquals(1200L, GTOConfigCompat.limitBatchProcessingDurationTicks(1_000_000L));
        assertEquals(0L, GTOConfigCompat.limitBatchProcessingDurationTicks(0L));
    }
}
