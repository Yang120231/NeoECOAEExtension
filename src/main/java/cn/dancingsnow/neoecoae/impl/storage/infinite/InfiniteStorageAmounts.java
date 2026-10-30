package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.storage.StorageChangeLog;
import cn.dancingsnow.neoecoae.impl.storage.persistence.StorageAmounts;

/** Compatibility name for the shared server-thread quantity table; records changed keys for incremental listing. */
final class InfiniteStorageAmounts extends StorageAmounts {
    final StorageChangeLog changes = new StorageChangeLog();

    @Override protected void changed(AEKey key) { changes.record(key); }

    @Override protected void cleared() { changes.invalidate(); }
}
