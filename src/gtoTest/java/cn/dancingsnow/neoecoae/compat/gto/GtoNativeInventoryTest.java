package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.persistence.StorageAmounts;
import java.math.BigInteger;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GtoNativeInventoryTest {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @Test void reservationsSaturateAfterSubtractionAcrossAllNativeBoundaries() {
        Random random = new Random(47126);
        for (int i = 0; i < 20_000; i++) {
            BigInteger value = new BigInteger(random.nextInt(127), random);
            long removed = switch (i % 7) {
                case 0 -> Long.MIN_VALUE;
                case 1 -> Long.MAX_VALUE;
                default -> random.nextLong();
            };
            long protectedAmount = random.nextLong() & Long.MAX_VALUE;
            long expected = value.subtract(BigInteger.valueOf(removed)).subtract(BigInteger.valueOf(protectedAmount))
                .max(BigInteger.ZERO).min(LONG_MAX).longValueExact();
            var nativeValue = GtoAmounts.fromExact(value);
            assertEquals(expected, GtoAmounts.available(nativeValue, removed, protectedAmount));
            long base = random.nextLong() & Long.MAX_VALUE;
            long withBase = value.add(BigInteger.valueOf(base)).subtract(BigInteger.valueOf(removed))
                .subtract(BigInteger.valueOf(protectedAmount)).max(BigInteger.ZERO).min(LONG_MAX).longValueExact();
            assertEquals(withBase, GtoAmounts.available(nativeValue, base, removed, protectedAmount));
            var copied = cn.dancingsnow.neoecoae.crafting.amount.U126.fromGto(nativeValue);
            assertEquals(value, copied.toBigInteger());
            for (long divisor : new long[]{1, 8, 1024, 8000, 1L << 62}) {
                assertEquals(value.divide(BigInteger.valueOf(divisor)), copied.divide(divisor).toBigInteger());
                assertEquals(value.remainder(BigInteger.valueOf(divisor)).longValueExact(), copied.remainder(divisor));
            }
        }
    }

    @Test void overflowReturnsToNativeStorageWithoutTruncation() {
        var key = new ECOGtoStorageIdentityTest.Key();
        var storage = new StorageAmounts();
        storage.set(key, HugeAmount.of(GtoAmounts.MAX.subtract(BigInteger.valueOf(3))));
        storage.add(key, 10);
        assertTrue(storage.hasOverflow());
        assertEquals(GtoAmounts.MAX.add(BigInteger.valueOf(7)), storage.get(key).toBigInteger());
        assertEquals(8, storage.extract(key, 8));
        assertFalse(storage.hasOverflow());
        assertEquals(GtoAmounts.MAX.subtract(BigInteger.ONE), storage.get(key).toBigInteger());
        storage.setLong(key, 9);
        assertEquals(9, storage.extract(key, 20));
        assertTrue(storage.isEmpty());
        assertEquals(BigInteger.ZERO, storage.totalExact());
        assertFalse(storage.visibleAmounts().containsKey(key));
    }

    @Test void exactModeTransitionsKeepTheCompatibilityWindowAndAllOwnedStock() {
        var key = new ECOGtoStorageIdentityTest.Key();
        var inventory = new ECOExactInventory(ignored -> {});
        inventory.insert(key, 41, Actionable.MODULATE);
        inventory.setEnabled(true);
        inventory.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        assertEquals(LONG_MAX.add(BigInteger.valueOf(41)), inventory.amount(key));
        inventory.setEnabled(false);
        assertTrue(inventory.isEnabled());
        assertEquals(42, inventory.extract(key, 42, Actionable.SIMULATE));
        assertEquals(LONG_MAX.add(BigInteger.valueOf(41)), inventory.amount(key));
        assertEquals(42, inventory.extract(key, 42, Actionable.MODULATE));
        inventory.setEnabled(false);
        assertFalse(inventory.isEnabled());
        assertEquals(Long.MAX_VALUE - 1, inventory.list.get(key));
        inventory.setEnabled(true);
        assertEquals(LONG_MAX.subtract(BigInteger.ONE), inventory.amount(key));
        inventory.insert(key, 100, Actionable.MODULATE);
        inventory.list.set(key, 5);
        assertEquals(0, inventory.extract(key, 0, Actionable.MODULATE));
        assertEquals(5, inventory.list.get(key), "An empty extraction must not rewrite AE2's live window");
    }

    @Test void nativeWideStacksStillExposeTheExactAmountContract() {
        var key = new ECOGtoStorageIdentityTest.Key();
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        engine.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        assertFalse(engine.hasHugeStacks());
        engine.insert(key, 1, Actionable.MODULATE);
        assertTrue(engine.hasHugeStacks());
        assertEquals(LONG_MAX.add(BigInteger.ONE), engine.getAmount(key).toBigInteger());
        engine.extract(key, 1, Actionable.MODULATE);
        assertFalse(engine.hasHugeStacks());
    }

    @Test void batchUnderflowAndCallbackFailureCannotConsumePartialInputs() {
        AEKey first = new ECOGtoStorageIdentityTest.Key(), second = new ECOGtoStorageIdentityTest.Key();
        AtomicBoolean fail = new AtomicBoolean();
        var inventory = new ECOExactInventory(key -> { if (fail.get()) throw new IllegalStateException("listener failed"); });
        inventory.setEnabled(true);
        BigInteger large = BigInteger.TEN.pow(40);
        inventory.restore(Map.of(first, large, second, BigInteger.valueOf(6)));
        assertFalse(inventory.debit(Map.of(first, large, second, BigInteger.valueOf(7))));
        assertEquals(large, inventory.amount(first));
        fail.set(true);
        assertThrows(IllegalStateException.class, () -> inventory.debit(Map.of(first, BigInteger.TEN, second, BigInteger.ONE)));
        assertEquals(large, inventory.amount(first));
        assertEquals(BigInteger.valueOf(6), inventory.amount(second));
        fail.set(false);
        assertTrue(inventory.debit(Map.of(first, large, second, BigInteger.valueOf(6))));
        assertTrue(inventory.isEmpty());
    }
}
