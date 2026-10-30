package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ECOInfiniteStorageMigrationBudgetTest {
    private static final List<AEKey> KEYS = GtoTestKeys.create(12);

    @Test void timeBudgetStopsAtDeadlineAndAlwaysAdvancesOneResource() {
        var clock = new AtomicLong();
        var engine = new FakeEngine(clock, 1_000L);
        var cell = cell(10);
        var transfer = new ECOInfiniteStorageTransfer();
        UUID migration = UUID.randomUUID();
        Object source = new Object();

        // 2.5 us budget, each insert costs 1 us: the third insert crosses the deadline.
        var result = transfer.step(migration, source, cell, engine, () -> {}, () -> {}, (k, t, a) -> UUID.randomUUID(),
            new ECOInfiniteStorageTransfer.Budget(clock::get, 2_500L, 512));
        assertEquals(ECOInfiniteStorageTransfer.State.IN_PROGRESS, result.state());
        assertEquals(3, engine.amounts.size());

        // A zero budget still moves exactly one resource per tick.
        for (int tick = 0; tick < 3; tick++) {
            int before = engine.amounts.size();
            result = transfer.step(migration, source, cell, engine, () -> {}, () -> {}, (k, t, a) -> UUID.randomUUID(),
                new ECOInfiniteStorageTransfer.Budget(clock::get, 0L, 512));
            assertEquals(ECOInfiniteStorageTransfer.State.IN_PROGRESS, result.state());
            assertEquals(before + 1, engine.amounts.size());
        }
    }

    @Test void resourceCapIsSharedAcrossSourcesInOneTick() {
        var clock = new AtomicLong();
        var engine = new FakeEngine(clock, 0L);
        var first = cell(0, 3);
        var second = cell(3, 4);
        var transfer = new ECOInfiniteStorageTransfer();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var completed = new ArrayList<String>();
        var budget = new ECOInfiniteStorageTransfer.Budget(clock::get, Long.MAX_VALUE / 4, 5);

        assertTrue(transfer.step(a, "a", first, engine, () -> {}, () -> completed.add("a"),
            (k, t, x) -> UUID.randomUUID(), budget).complete());
        assertFalse(budget.exhausted());
        var second1 = transfer.step(b, "b", second, engine, () -> {}, () -> completed.add("b"),
            (k, t, x) -> UUID.randomUUID(), budget);
        assertEquals(ECOInfiniteStorageTransfer.State.IN_PROGRESS, second1.state());
        assertTrue(budget.exhausted());
        assertEquals(5, engine.amounts.size(), "the second source continues with the remaining allowance");
        assertEquals(List.of("a"), completed);
        assertEquals(0.5D, transfer.progress(b), 1e-9);

        assertTrue(transfer.step(b, "b", second, engine, () -> {}, () -> completed.add("b"),
            (k, t, x) -> UUID.randomUUID(), ECOInfiniteStorageTransfer.Budget.keys(512)).complete());
        assertEquals(List.of("a", "b"), completed, "sources finish in order");
        assertEquals(7, engine.amounts.size());
    }

    @Test void progressOfOneSourceAndTheHostNeverDecreases() {
        var clock = new AtomicLong();
        var engine = new FakeEngine(clock, 0L);
        var cell = cell(7);
        var transfer = new ECOInfiniteStorageTransfer();
        UUID migration = UUID.randomUUID();
        double last = 0;
        int lastPercent = 0;
        ECOInfiniteStorageTransfer.Result result;
        do {
            result = transfer.step(migration, "s", cell, engine, () -> {}, () -> {}, (k, t, a) -> UUID.randomUUID(),
                ECOInfiniteStorageTransfer.Budget.keys(2));
            double progress = result.complete() ? 1.0D : transfer.progress(migration);
            assertTrue(progress >= last);
            last = progress;
            int percent = cn.dancingsnow.neoecoae.blocks.entity.storage.MigrationProgressAccess.percent(
                result.complete() ? 2 : 1, 3, result.complete() ? 0 : progress);
            assertTrue(percent >= lastPercent, "host percent must be monotonic");
            assertTrue(percent < 100, "100% is reserved for the switched host");
            lastPercent = percent;
        } while (!result.complete());
        assertEquals(66, lastPercent);
    }

    @Test void refusedResourceAndFailedCommitAreReportedWithoutCompleting() {
        var clock = new AtomicLong();
        var engine = new FakeEngine(clock, 0L);
        var cell = cell(2);
        var transfer = new ECOInfiniteStorageTransfer();
        UUID migration = UUID.randomUUID();
        var completed = new AtomicInteger();

        engine.refuse = "Unresolved entries retained: 1";
        var blocked = transfer.step(migration, "s", cell, engine, () -> {}, completed::incrementAndGet,
            (k, t, a) -> UUID.randomUUID(), ECOInfiniteStorageTransfer.Budget.keys(512));
        assertTrue(blocked.blocked());
        assertTrue(blocked.domainWide());
        assertEquals("Unresolved entries retained: 1", blocked.reason());

        engine.refuse = null;
        engine.commitFailure = "disk full";
        blocked = transfer.step(migration, "s", cell, engine, () -> {}, completed::incrementAndGet,
            (k, t, a) -> UUID.randomUUID(), ECOInfiniteStorageTransfer.Budget.keys(512));
        assertTrue(blocked.blocked());
        assertEquals("disk full", blocked.reason());
        assertEquals(0, completed.get(), "only a successful commit may retire the source");

        engine.commitFailure = null;
        engine.flushOk = false;
        assertTrue(transfer.step(migration, "s", cell, engine, () -> {}, completed::incrementAndGet,
            (k, t, a) -> UUID.randomUUID(), ECOInfiniteStorageTransfer.Budget.keys(512)).blocked());
        engine.flushOk = true;
        assertTrue(transfer.step(migration, "s", cell, engine, () -> {}, completed::incrementAndGet,
            (k, t, a) -> UUID.randomUUID(), ECOInfiniteStorageTransfer.Budget.keys(512)).complete());
        assertEquals(1, completed.get());
        assertEquals(2, engine.amounts.size(), "retries reuse receipts instead of inserting twice");
        engine.amounts.forEach((key, amount) -> assertEquals(BigInteger.valueOf(100), amount));
    }

    @Test void receiptsKeepTheirOriginalBytesAndShareOneSerialization() {
        var clock = new AtomicLong();
        var engine = new FakeEngine(clock, 0L);
        var cell = cell(4);
        var transfer = new ECOInfiniteStorageTransfer();
        UUID migration = UUID.randomUUID(), domain = UUID.randomUUID();
        long position = 123_456_789L;
        assertTrue(transfer.step(migration, "s", cell, engine, () -> {}, () -> {}, (key, keyTag, amount) -> {
            assertEquals(key.toTagGeneric().toString(), keyTag);
            return UUID.nameUUIDFromBytes((domain + ":to-domain:" + position + ":" + keyTag + ":" + amount)
                .getBytes(StandardCharsets.UTF_8));
        }, ECOInfiniteStorageTransfer.Budget.keys(512)).complete());

        for (AEKey key : engine.amounts.keySet()) {
            // The exact expressions used before the change.
            UUID sealed = UUID.nameUUIDFromBytes((migration + ":" + key.toTagGeneric()).getBytes(StandardCharsets.UTF_8));
            UUID legacy = UUID.nameUUIDFromBytes((domain + ":" + "to-domain" + ":" + position + ":"
                + key.toTagGeneric() + ":" + 100L).getBytes(StandardCharsets.UTF_8));
            assertEquals(sealed, ECOInfiniteStorageTransfer.sealedReceipt(migration, key.toTagGeneric().toString()));
            assertEquals(Set.of(legacy, sealed), engine.receiptsByKey.get(key));
            assertEquals(legacy, cn.dancingsnow.neoecoae.blocks.entity.storage.MigrationProgressAccess.legacyReceipt(
                domain, "to-domain", position, key.toTagGeneric().toString(), 100L));
        }
    }

    private static IECOStorageMigrationCell cell(int types) {
        return cell(0, types);
    }

    private static IECOStorageMigrationCell cell(int first, int types) {
        var contents = new KeyCounter();
        for (int i = first; i < first + types; i++) contents.add(KEYS.get(i), 100);
        IECOStorageMigrationCell cell = mock(IECOStorageMigrationCell.class);
        when(cell.isInfiniteStorageEligible()).thenReturn(true);
        when(cell.getStoredItemTypes()).thenReturn((long) types);
        doAnswer(call -> contents.iterator()).when(cell).migrationEntries();
        return cell;
    }

    /** Minimal receipt-aware destination; each insert advances the injected clock. */
    private static final class FakeEngine implements ECOInfiniteStorageEngine {
        final Map<AEKey, BigInteger> amounts = new HashMap<>();
        final Map<AEKey, Set<UUID>> receiptsByKey = new HashMap<>();
        final Set<UUID> receipts = new HashSet<>();
        final AtomicLong clock;
        final long insertCost;
        String refuse;
        String commitFailure;
        boolean flushOk = true;

        FakeEngine(AtomicLong clock, long insertCost) { this.clock = clock; this.insertCost = insertCost; }

        @Override public long insertOnce(Set<UUID> ids, AEKey key, long amount) {
            if (refuse != null) return 0;
            clock.addAndGet(insertCost);
            if (ids.stream().anyMatch(receipts::contains)) return amount;
            receipts.addAll(ids);
            receiptsByKey.put(key, Set.copyOf(ids));
            amounts.merge(key, BigInteger.valueOf(amount), BigInteger::add);
            return amount;
        }
        @Override public String migrationBlockReason() { return refuse; }
        @Override public CommitResult commit() {
            return new CommitResult(commitFailure == null, 0, commitFailure);
        }
        @Override public boolean flush() { return flushOk; }
        @Override public long insert(AEKey key, long amount, Actionable mode) { return 0; }
        @Override public BigInteger insert(AEKey key, BigInteger amount, Actionable mode) { return BigInteger.ZERO; }
        @Override public BigInteger usedBytes() { return BigInteger.ZERO; }
        @Override public long extract(AEKey key, long amount, Actionable mode) { return 0; }
        @Override public HugeAmount getAmount(AEKey key) { return HugeAmount.ZERO; }
        @Override public void getAvailableStacks(KeyCounter out) {}
        @Override public boolean isEmpty() { return amounts.isEmpty(); }
        @Override public Collection<TypeStats> getTypeStats() { return List.of(); }
        @Override public boolean hasHugeStacks() { return false; }
        @Override public boolean reserveRestores(Map<AEKey, UUID> transactions, Set<UUID> targets,
                                                 Map<AEKey, Map<UUID, RestoreTargetAmounts>> plans) { return false; }
        @Override public Map<UUID, RestoreTargetAmounts> restorePlan(AEKey key) { return Map.of(); }
        @Override public boolean finishRestores(Map<AEKey, UUID> transactions) { return false; }
    }
}
