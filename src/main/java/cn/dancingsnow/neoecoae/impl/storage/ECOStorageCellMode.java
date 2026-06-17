package cn.dancingsnow.neoecoae.impl.storage;

public enum ECOStorageCellMode {
    PORTABLE,
    MIGRATING,
    DOMAIN_MEMBER;

    public static ECOStorageCellMode byName(String name) {
        if (name == null || name.isBlank()) {
            return PORTABLE;
        }
        try {
            return ECOStorageCellMode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return PORTABLE;
        }
    }
}
