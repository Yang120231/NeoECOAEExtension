package cn.dancingsnow.neoecoae.impl.storage;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import net.minecraft.world.item.ItemStack;

/** Cell-local priorities use AE2's signed integer ordering: higher values receive inserts first. */
public final class ECOStorageCellSettings {
    private static final String PRIORITY = "neoecoae_storage_priority";

    private ECOStorageCellSettings() {}

    public static boolean canConfigure(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof ECOInfiniteResourceCellItem)
            && !ECOInfiniteStorageMember.isMember(stack) && !ECOInfiniteStorageMember.isMigrating(stack);
    }

    public static int priority(ItemStack stack) {
        return stack.isEmpty() ? 0 : NEItemData.read(stack).getInt(PRIORITY);
    }

    public static void setPriority(ItemStack stack, int priority) {
        // The default leaves no tag behind, so an unconfigured cell keeps its original item data.
        NEItemData.update(stack, tag -> {
            if (priority == 0) tag.remove(PRIORITY);
            else tag.putInt(PRIORITY, priority);
        });
    }
}
