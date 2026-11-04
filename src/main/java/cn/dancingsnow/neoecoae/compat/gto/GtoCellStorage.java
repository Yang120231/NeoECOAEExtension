package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyLongMap;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage.AvailableStacksCache;
import com.gtolib.api.ae2.storage.CellDataStorage;

/** GTOLib's ordinary cell API, sharing ECO's quantity table and persistence owner. */
public final class GtoCellStorage {
    private final CellDataStorage delegate = new CellDataStorage();
    private final AEKeyLongMap<AEKey> amounts;
    private final AvailableStacksCache cache;
    private boolean listingDirty = true;

    private GtoCellStorage(AEKeyLongMap<AEKey> amounts) {
        this.amounts = amounts;
        delegate.setStoredMap(amounts);
        cache = delegate.cache;
        refresh();
    }

    public static GtoCellStorage create(AEKeyLongMap<AEKey> amounts) {
        return new GtoCellStorage(amounts);
    }

    public CellDataStorage identity() { return delegate; }
    public AvailableStacksCache cache() { return cache; }

    public KeyCounter listing() {
        if (listingDirty) {
            cache.updateCache();
            listingDirty = false;
        }
        return cache.getAvailableStacksCache();
    }

    public void changed(AEKey key, long delta) {
        if (delta != 0) delegate.addBytes(key, delta);
        delegate.markChanged(key);
        listingDirty = true;
    }

    public void refresh() {
        double[] bytes = {0};
        amounts.fastForEach((key, amount) -> bytes[0] += (double) amount / key.getAmountPerByte());
        delegate.setBytes(bytes[0]);
        cache.invalidateCache();
        listingDirty = true;
    }
}
