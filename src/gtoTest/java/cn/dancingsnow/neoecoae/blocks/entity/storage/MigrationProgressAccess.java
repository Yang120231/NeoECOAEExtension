package cn.dancingsnow.neoecoae.blocks.entity.storage;

/** Test bridge to the package-private host migration helpers. */
public final class MigrationProgressAccess {
    private MigrationProgressAccess() {}

    public static int percent(int completed, int total, double current) {
        return ECOInfiniteStorageModeController.progressPercent(completed, total, current);
    }

    public static java.util.UUID legacyReceipt(java.util.UUID domain, String direction, long position, String keyTag,
                                               long amount) {
        return ECOInfiniteStorageModeController.legacyTransactionId(domain, direction, position, keyTag, amount);
    }
}
