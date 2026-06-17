package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.storage.cells.ISaveProvider;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public interface IECOStorageCellHostContext extends ISaveProvider {
    boolean isInfiniteStorageUnlocked();

    @Nullable default ServerLevel getStorageLevel() {
        return null;
    }
}
