package cn.dancingsnow.neoecoae.impl.storage;

public enum ECOStorageHostMode {
    UNFORMED,
    FORMED_NORMAL,
    MIGRATING_TO_INFINITE,
    FORMED_INFINITE;

    public static ECOStorageHostMode byName(String name) {
        if (name == null || name.isBlank()) {
            return UNFORMED;
        }
        try {
            return ECOStorageHostMode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return UNFORMED;
        }
    }
}
