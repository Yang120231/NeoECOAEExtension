package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.CellState;
import org.junit.jupiter.api.Test;

class ECOCellCapacityTest {
    private static final long TOTAL_BYTES = 16L * 1024L * 1024L;
    private static final int BYTES_PER_TYPE = 8192;

    @Test
    void acceptsANewTypeBelow315() {
        ECOCellCapacity capacity = capacity(315, 314);

        assertEquals(1, capacity.remainingItemTypes());
        assertTrue(capacity.canHoldNewItem());
        assertEquals(CellState.NOT_EMPTY, capacity.status());
    }

    @Test
    void rejectsANewTypeAt315() {
        ECOCellCapacity capacity = capacity(315, 315);

        assertEquals(0, capacity.remainingItemTypes());
        assertFalse(capacity.canHoldNewItem());
        assertEquals(CellState.TYPES_FULL, capacity.status());
    }

    @Test
    void preservesAnOverLimitCellWithoutReportingNegativeCapacity() {
        ECOCellCapacity capacity = capacity(315, 400);

        assertEquals(0, capacity.remainingItemTypes());
        assertFalse(capacity.canHoldNewItem());
        assertEquals(CellState.TYPES_FULL, capacity.status());
    }

    @Test
    void unlimitedCellAcceptsNewTypesAbove315() {
        ECOCellCapacity capacity = capacity(Long.MAX_VALUE, 400);

        assertTrue(capacity.remainingItemTypes() > 0);
        assertTrue(capacity.canHoldNewItem());
        assertEquals(CellState.NOT_EMPTY, capacity.status());
    }

    private static ECOCellCapacity capacity(long totalTypes, long storedTypes) {
        return new ECOCellCapacity(
                AEKeyType.items(), TOTAL_BYTES, BYTES_PER_TYPE, totalTypes, storedTypes, storedTypes);
    }
}
