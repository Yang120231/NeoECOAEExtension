package cn.dancingsnow.neoecoae.impl.storage;

final class ECOStorageInsertPolicy {
    private ECOStorageInsertPolicy() {}

    static boolean blocksInsert(boolean alreadyStored, long storedTypes, long totalTypes) {
        return !alreadyStored && storedTypes >= totalTypes;
    }
}
