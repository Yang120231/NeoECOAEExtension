package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ICellWorkbenchItem;
import cn.dancingsnow.neoecoae.api.IECOTier;
import net.minecraft.world.item.ItemStack;

public interface IBasicECOCellItem extends ICellWorkbenchItem {
    IECOTier getTier();

    AEKeyType getKeyType();

    default boolean acceptsKey(AEKey what) {
        return getKeyType().contains(what);
    }

    default int getAmountPerByte(AEKey what) {
        return getKeyType().getAmountPerByte();
    }

    default boolean isUniversalStorage() {
        return false;
    }

    long getBytes();

    default long getIdleDrainBytes() {
        return getBytes();
    }

    int getBytesPerType();

    int getTotalTypes();

    ECOCellType getCellType();

    default boolean isBlackListed(ItemStack cellStack, AEKey what) {
        return false;
    }
}
