package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.impl.storage.StorageChangeLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/**
 * Replays GTO's StorageService mount refresh (version skip, journal drain, full re-list on {@code false}) and checks
 * that the incrementally maintained listing always equals a fresh {@code getAvailableStacks}.
 */
class ECOInfiniteStorageIncrementalListingTest {
    private static final List<AEKey> KEYS = GtoTestKeys.create(301);
    private static final AEKey EXTRA = KEYS.remove(300);

    @Test void journalMatchesFullListingUnderRandomTraffic() {
        var data = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(data);
        boolean[] enabled = {true};
        var view = new ECOInfiniteStorage(engine, Component.empty(), () -> enabled[0]);
        var first = new Reader(view);
        var second = new Reader(view);
        Random random = new Random(20261028);
        Map<AEKey, UUID> reserved = new HashMap<>();
        for (AEKey key : KEYS) engine.insert(key, 1 + random.nextInt(1000), Actionable.MODULATE);
        first.refresh();
        second.refresh();
        for (int step = 0; step < 6_000; step++) {
            AEKey key = KEYS.get(random.nextInt(KEYS.size()));
            int action = random.nextInt(100);
            if (action < 45) engine.insert(key, 1 + random.nextInt(5_000), Actionable.MODULATE);
            else if (action < 85) engine.extract(key, 1 + random.nextInt(5_000), Actionable.MODULATE);
            else if (action < 88) engine.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
            else if (action < 90) engine.extract(key, Long.MAX_VALUE, Actionable.MODULATE);
            else if (action < 92 && reserved.size() < 4 && engine.contains(key)) {
                UUID tx = UUID.randomUUID();
                if (data.reserveRestore(key, tx, Set.of(), Map.of())) reserved.put(key, tx);
            } else if (action < 94 && !reserved.isEmpty()) {
                var entry = reserved.entrySet().iterator().next();
                assertTrue(data.finishRestore(entry.getKey(), entry.getValue()));
                reserved.remove(entry.getKey());
            } else if (action < 95) {
                enabled[0] = !enabled[0];
            } else if (action < 96) {
                // FE-like hot key: many changes of one key between two refreshes.
                for (int i = 0; i < 50; i++) engine.insert(KEYS.get(0), 7, Actionable.MODULATE);
            }
            if (step % 3 == 0) first.refreshAndCheck();
            if (step % 7 == 0) second.refreshAndCheck();
        }
        enabled[0] = true;
        first.refreshAndCheck();
        second.refreshAndCheck();
        assertTrue(first.drained > 100, "the journal path must actually be used");
        assertTrue(second.drained > 50);
    }

    @Test void overflowAndStructuralChangesFallBackToFullListing() {
        var data = ECOInfiniteStorageData.createNew();
        var engine = new SavedDataInfiniteStorageEngine(data);
        var view = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
        for (AEKey key : KEYS) engine.insert(key, 10, Actionable.MODULATE);
        var reader = new Reader(view);
        reader.refresh();

        // More distinct records than the ring holds.
        for (int round = 0; round <= StorageChangeLog.CAPACITY / KEYS.size() + 1; round++)
            for (AEKey key : KEYS) engine.insert(key, 1, Actionable.MODULATE);
        int full = reader.full;
        reader.refreshAndCheck();
        assertEquals(full + 1, reader.full);

        // A restore reservation hides a key without changing its amount.
        UUID tx = UUID.randomUUID();
        assertTrue(data.reserveRestore(KEYS.get(5), tx, Set.of(), Map.of()));
        full = reader.full;
        reader.refreshAndCheck();
        assertEquals(full + 1, reader.full);
        assertEquals(0, reader.content.get(KEYS.get(5)));

        // Plain quantity changes after that stay incremental.
        engine.extract(KEYS.get(6), Long.MAX_VALUE, Actionable.MODULATE);
        engine.insert(EXTRA, 1000, Actionable.MODULATE);
        int drained = reader.drained;
        reader.refreshAndCheck();
        assertEquals(drained + 1, reader.drained);
        assertEquals(0, reader.content.get(KEYS.get(6)));
    }

    @Test void consecutiveWritesOfOneKeyAreMergedOnlyUntilARead() {
        var log = new StorageChangeLog();
        AEKey a = KEYS.get(0), b = KEYS.get(1);
        var start = log.cursor();
        log.record(a);
        log.record(a);
        assertEquals(1, log.sequence() - start.sequence());
        var afterRead = log.cursor();
        log.record(a);
        List<AEKey> seen = new ArrayList<>();
        assertTrue(log.visitSince(afterRead, seen::add));
        assertEquals(List.of(a), seen, "a write after a read must be visible to that reader");
        seen.clear();
        log.record(b);
        log.record(a);
        assertTrue(log.visitSince(start, seen::add));
        assertEquals(List.of(a, b), seen, "each key is reported once");
        log.invalidate();
        assertFalse(log.visitSince(afterRead, seen::add));
    }

    /** Mirrors StorageService.refreshMount for one mount. */
    private static final class Reader {
        final MEStorage view;
        final KeyCounter content = new KeyCounter();
        long version = -1;
        boolean journaled;
        int drained;
        int full;

        Reader(MEStorage view) { this.view = view; }

        void refresh() {
            long next = view.getChangeVersion();
            if (next >= 0 && next == version) return;
            if (journaled && view.drainChanges(this, (key, amount) -> {
                if (amount == 0) content.remove(key); else content.set(key, amount);
            })) {
                version = next;
                drained++;
                return;
            }
            content.clear();
            view.getAvailableStacks(content);
            version = next;
            journaled = next >= 0;
            if (journaled) view.drainChanges(this, MEStorage.IGNORE_CHANGES);
            full++;
        }

        void refreshAndCheck() {
            refresh();
            KeyCounter fresh = new KeyCounter();
            view.getAvailableStacks(fresh);
            fresh.removeZeros();
            content.removeZeros();
            assertEquals(fresh.size(), content.size());
            for (var entry : fresh) assertEquals(entry.getLongValue(), content.get(entry.getKey()), entry.getKey().toString());
        }
    }
}
