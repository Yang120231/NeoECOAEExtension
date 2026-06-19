package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class NEStorageMetricsModelTest {
    @Test
    void finiteBytesWithInfiniteTypesStillUseBytePercentage() {
        var metric = new NEStorageMetricsModel.Metric(
                "neoecoae:items",
                Component.translatable("gui.neoecoae.storage.items"),
                6_801_991L,
                268_435_456L,
                790L,
                Long.MAX_VALUE,
                0xFF43B678,
                "6,801,991",
                "268,435,456",
                "790",
                "\u221e",
                true,
                true);

        assertFalse(metric.infiniteCapacity());
        assertEquals(6_801_991D / 268_435_456D, metric.percent(), 1.0E-9D);
    }
}
