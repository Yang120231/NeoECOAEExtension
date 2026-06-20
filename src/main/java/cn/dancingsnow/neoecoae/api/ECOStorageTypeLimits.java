package cn.dancingsnow.neoecoae.api;

public final class ECOStorageTypeLimits {
    public static final int FINITE_TYPE_LIMIT = 315;
    private static final int UNLIMITED_TIER = 3;

    private ECOStorageTypeLimits() {}

    public static int forTier(int tier) {
        return hasFiniteLimit(tier) ? FINITE_TYPE_LIMIT : Integer.MAX_VALUE;
    }

    public static boolean hasFiniteLimit(int tier) {
        return tier < UNLIMITED_TIER;
    }
}
