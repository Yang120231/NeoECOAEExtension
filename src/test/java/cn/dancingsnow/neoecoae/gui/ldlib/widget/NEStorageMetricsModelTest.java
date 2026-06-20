package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    @Test
    void unlimitedL9TypeCapacityDisplaysAsInfinityOutsideInfiniteDomainMode() {
        var itemTypes = new NEStorageUiTypeState(
                new ResourceLocation("neoecoae", "items"), "Items", 400L, Long.MAX_VALUE, 10_000L, 1_000_000L);
        var state = new NEStorageUiState(
                BlockPos.ZERO,
                List.of(itemTypes),
                List.of(),
                0L,
                0L,
                true,
                false,
                0,
                64,
                false,
                false,
                0,
                0,
                0L,
                1_000_000_000_001L);

        var metrics = NEStorageMetricsModel.from(state);

        assertEquals("\u221e", metrics.types().get(0).totalTypesText());
    }
}
