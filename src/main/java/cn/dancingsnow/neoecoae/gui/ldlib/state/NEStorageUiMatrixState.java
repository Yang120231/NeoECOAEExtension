package cn.dancingsnow.neoecoae.gui.ldlib.state;

import net.minecraft.world.item.ItemStack;

public record NEStorageUiMatrixState(
        int row,
        int column,
        ItemStack stack,
        int tier,
        long usedTypes,
        long totalTypes,
        long usedBytes,
        long totalBytes,
        boolean infiniteMember,
        ItemStack previewStack) {
    public NEStorageUiMatrixState(
            int row,
            int column,
            ItemStack stack,
            int tier,
            long usedTypes,
            long totalTypes,
            long usedBytes,
            long totalBytes,
            boolean infiniteMember) {
        this(row, column, stack, tier, usedTypes, totalTypes, usedBytes, totalBytes, infiniteMember, ItemStack.EMPTY);
    }

    public NEStorageUiMatrixState(
            int row,
            int column,
            ItemStack stack,
            int tier,
            long usedTypes,
            long totalTypes,
            long usedBytes,
            long totalBytes) {
        this(row, column, stack, tier, usedTypes, totalTypes, usedBytes, totalBytes, false);
    }

    public boolean hasMatrix() {
        return !stack.isEmpty();
    }
}
