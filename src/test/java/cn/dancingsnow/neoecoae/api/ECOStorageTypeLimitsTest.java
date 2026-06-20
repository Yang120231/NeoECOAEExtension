package cn.dancingsnow.neoecoae.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ECOStorageTypeLimitsTest {
    @Test
    void l4AndL6Use315TypesForEveryStorageKind() {
        assertEquals(315, ECOStorageTypeLimits.forTier(1));
        assertEquals(315, ECOStorageTypeLimits.forTier(2));
        assertTrue(ECOStorageTypeLimits.hasFiniteLimit(1));
        assertTrue(ECOStorageTypeLimits.hasFiniteLimit(2));
    }

    @Test
    void l9HasNoFiniteTypeLimit() {
        assertEquals(Integer.MAX_VALUE, ECOStorageTypeLimits.forTier(3));
        assertFalse(ECOStorageTypeLimits.hasFiniteLimit(3));
    }
}
