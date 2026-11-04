package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyBigMap;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage.AvailableStacksCache;

/**
 * Connects ECO's quantity table to GTOLib's public BigCellDataStorage API.
 *
 * <p>Each ECO SavedData owns one native instance. ECO's atomic snapshot remains the persistence owner
 * for quantities, migration receipts and ownership; this instance is not registered in GTOLib's UUID store.
 */
public final class GtoBigCellStorage {
    private final Object delegate;
    private final AEKeyBigMap<AEKey> amounts;
    private final AvailableStacksCache cache;
    private boolean listingDirty = true;

    private GtoBigCellStorage(AEKeyBigMap<AEKey> amounts) {
        this.amounts = amounts;
        delegate = GtoRuntimeApi.createBigCell(amounts);
        cache = GtoRuntimeApi.bigCellCache(delegate);
        refresh();
    }

    public static GtoBigCellStorage create(AEKeyBigMap<AEKey> amounts) {
        return new GtoBigCellStorage(amounts);
    }

    public Object identity() { return delegate; }
    public AEKeyBigMap<AEKey> amounts() { return amounts; }
    public AvailableStacksCache cache() { return cache; }

    public KeyCounter listing() {
        if (listingDirty) {
            // ECO permits multiple mutations and reads within one tick.
            cache.updateCache();
            listingDirty = false;
        }
        return cache.getAvailableStacksCache();
    }

    public void changed(AEKey key, long delta) {
        if (delta != 0) GtoRuntimeApi.addBigCellBytes(delegate, key, delta);
        // ECO owns persistence; notify the native cache without enlisting GTOLib's UUID store.
        cache.markAsDirty(key);
        listingDirty = true;
    }

    /** Bulk replacement/legacy decoding is a cold path; ordinary IO adjusts bytes incrementally. */
    public void refresh() {
        double[] bytes = {0};
        amounts.forEach((key, amount) -> bytes[0] += amount.doubleValue() / key.getAmountPerByte());
        GtoRuntimeApi.setBigCellBytes(delegate, bytes[0]);
        cache.invalidateCache();
        listingDirty = true;
    }
}
