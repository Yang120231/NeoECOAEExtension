package cn.dancingsnow.neoecoae.impl.storage;

/**
 * Optional change marker for a mounted inventory. Equal versions guarantee an identical
 * {@code getAvailableStacks} result, so AE2's network cache may reuse the previous listing.
 */
public interface StorageListingVersion {
    /** The listing may have changed; the inventory must be listed again. */
    long UNKNOWN = Long.MIN_VALUE;

    long neoecoae$listingVersion();

    default long getChangeVersion() {
        long version = neoecoae$listingVersion();
        return version < 0 ? -1 : version;
    }

    default boolean drainChanges(Object reader, java.util.function.ObjLongConsumer<appeng.api.stacks.AEKey> visitor) {
        return false;
    }

    /** Older GTO inventories without a change marker must be listed again. */
    static long versionOf(Object storage) {
        return storage instanceof StorageListingVersion versioned ? versioned.getChangeVersion() : -1;
    }

    static boolean drain(Object storage, Object reader,
            java.util.function.ObjLongConsumer<appeng.api.stacks.AEKey> visitor) {
        return storage instanceof StorageListingVersion versioned && versioned.drainChanges(reader, visitor);
    }
}
