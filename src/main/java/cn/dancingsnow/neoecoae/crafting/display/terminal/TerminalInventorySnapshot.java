package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.impl.storage.transfer.StorageBusTransferFilter;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.SupplierStorageAccessor;
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Server-thread publications shared by terminal viewers and planning; published counters are never updated. */
public record TerminalInventorySnapshot(KeyCounter inventory, Map<AEKey, ExactAmount> amounts) {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final Map<Object, Cached> CACHE = new WeakHashMap<>();

    public void visitVisibleAmounts(java.util.function.ObjLongConsumer<AEKey> visitor) {
        inventory.fastForEach(visitor);
    }

    public static TerminalInventorySnapshot sample(Object storage, long tick, boolean exact,
            Supplier<KeyCounter> listing) {
        return sample(storage, tick, exact, false, listing);
    }

    /** A planning request samples ordinary and exact quantities together, including writes earlier in this tick. */
    public static TerminalInventorySnapshot sample(Object storage, long tick, boolean exact, boolean refreshExact,
            Supplier<KeyCounter> listing) {
        if (ExactAmountCollector.isActive()) return new TerminalInventorySnapshot(listing.get().copy(), Map.of());
        // A filtered listing cannot publish into an ordinary viewer's cache or consume its previous baseline.
        Cached cached = StorageBusTransferFilter.isActive() ? new Cached()
            : CACHE.computeIfAbsent(storage, ignored -> new Cached());
        if (tick < cached.tick) cached.reset();
        boolean collect = exact && (refreshExact || cached.exactTick == Long.MIN_VALUE
            || tick - cached.exactTick >= MenuDataTransport.UPDATE_INTERVAL);
        long version = cn.dancingsnow.neoecoae.impl.storage.StorageListingVersion.versionOf(storage);
        boolean changed = version >= 0 ? version != cached.version : cached.tick != tick;
        if (cached.snapshot == null || collect || changed) {
            try {
                cached.sample(collect, listing);
            } catch (RuntimeException | Error failure) {
                cached.reset();
                throw failure;
            }
            cached.version = version;
            if (collect) cached.exactTick = tick;
        }
        cached.tick = tick;
        return cached.snapshot;
    }

    /** Unwrap only AE2's transparent supplier; filtered inventory handlers keep separate caches. */
    public static MEStorage resolve(MEStorage storage) {
        MEStorage current = storage;
        for (int depth = 0; depth < 16; depth++) {
            if (!(current instanceof SupplierStorageAccessor supplier)) return current;
            MEStorage delegate = supplier.neoecoae$getDelegate();
            if (delegate == null || delegate == current) return current;
            current = delegate;
        }
        return storage;
    }

    public static void clear() { CACHE.clear(); }

    public static void invalidate(Object storage) { CACHE.remove(storage); }

    private static boolean sameContents(KeyCounter previous, KeyCounter current) {
        if (previous == current) return true;
        if (previous.size() != current.size()) return false;
        for (var entry : current) if (previous.get(entry.getKey()) != entry.getLongValue()) return false;
        return true;
    }

    private static final class Cached {
        private long tick = Long.MIN_VALUE;
        private long exactTick = Long.MIN_VALUE;
        private long version = -1;
        private TerminalInventorySnapshot snapshot;

        private void reset() { snapshot = null; tick = exactTick = Long.MIN_VALUE; version = -1; }

        private void sample(boolean collect, Supplier<KeyCounter> listing) {
            KeyCounter inventory;
            Map<AEKey, ExactAmount> amounts = snapshot == null ? Map.of() : snapshot.amounts();
            if (collect) {
                ExactAmountCollector.begin();
                try {
                    inventory = listing.get();
                    Map<AEKey, ExactAmount> collected = ExactAmountCollector.finish();
                    Map<AEKey, ExactAmount> needed = new HashMap<>();
                    collected.forEach((key, amount) -> {
                        if (amount.infinite() || amount.value().compareTo(MAX_LONG) > 0
                                || inventory.get(key) != amount.value().longValueExact()) needed.put(key, amount);
                    });
                    if (!needed.equals(amounts)) amounts = Map.copyOf(needed);
                } catch (RuntimeException | Error failure) {
                    ExactAmountCollector.abort();
                    throw failure;
                }
            } else inventory = listing.get();
            // GTO already has a flat primitive counter: retain it, without the ordinary branch's grouped-counter Mixin.
            KeyCounter published = snapshot != null && sameContents(snapshot.inventory(), inventory)
                ? snapshot.inventory() : inventory.copy();
            if (snapshot == null || published != snapshot.inventory() || amounts != snapshot.amounts()) {
                snapshot = new TerminalInventorySnapshot(published, amounts);
            }
        }
    }
}
