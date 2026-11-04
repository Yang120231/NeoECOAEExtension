package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyLongMap;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage.AvailableStacksCache;

/** GTOLib's ordinary cell API, sharing ECO's quantity table and persistence owner. */
public final class GtoCellStorage {
    private final Object delegate;
    private final AEKeyLongMap<AEKey> amounts;
    private final AvailableStacksCache cache;
    private boolean listingDirty = true;

    private GtoCellStorage(AEKeyLongMap<AEKey> amounts) {
        this.amounts = amounts;
        delegate = GtoRuntimeApi.createCell(amounts);
        cache = GtoRuntimeApi.cellCache(delegate);
        refresh();
    }

    public static GtoCellStorage create(AEKeyLongMap<AEKey> amounts) {
        return new GtoCellStorage(amounts);
    }

    public Object identity() { return delegate; }
    public AvailableStacksCache cache() { return cache; }

    public KeyCounter listing() {
        if (listingDirty) {
            cache.updateCache();
            listingDirty = false;
        }
        return cache.getAvailableStacksCache();
    }

    public void changed(AEKey key, long delta) {
        if (delta != 0) GtoRuntimeApi.addCellBytes(delegate, key, delta);
        // ECO owns persistence; notify the native cache without enlisting GTOLib's UUID store.
        cache.markAsDirty(key);
        listingDirty = true;
    }

    public void refresh() {
        double[] bytes = {0};
        amounts.fastForEach((key, amount) -> bytes[0] += (double) amount / key.getAmountPerByte());
        GtoRuntimeApi.setCellBytes(delegate, bytes[0]);
        cache.invalidateCache();
        listingDirty = true;
    }
}
