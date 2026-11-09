package cn.dancingsnow.neoecoae.impl.storage.infinite;

import cn.dancingsnow.neoecoae.impl.storage.persistence.StorageAmounts;

/** Infinite domains start with the GTO wide table and share ECO's quantity journal. */
final class InfiniteStorageAmounts extends StorageAmounts {
    InfiniteStorageAmounts() { super(true); }
}
