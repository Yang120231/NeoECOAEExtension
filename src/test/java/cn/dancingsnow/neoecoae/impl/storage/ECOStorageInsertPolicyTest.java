package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ECOStorageInsertPolicyTest {
    @Test
    void acceptsNewTypesBelowTheLimit() {
        assertFalse(ECOStorageInsertPolicy.blocksInsert(false, 314, 315));
    }

    @Test
    void rejectsNewTypesAtAndAboveTheLimit() {
        assertTrue(ECOStorageInsertPolicy.blocksInsert(false, 315, 315));
        assertTrue(ECOStorageInsertPolicy.blocksInsert(false, 400, 315));
    }

    @Test
    void acceptsExistingTypesEvenWhenTheCellIsOverLimit() {
        assertFalse(ECOStorageInsertPolicy.blocksInsert(true, 400, 315));
    }

    @Test
    void unlimitedCellsAcceptNewTypes() {
        assertFalse(ECOStorageInsertPolicy.blocksInsert(false, 400, Long.MAX_VALUE));
    }
}
