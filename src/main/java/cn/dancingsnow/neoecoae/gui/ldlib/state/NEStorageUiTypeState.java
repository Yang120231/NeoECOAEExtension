package cn.dancingsnow.neoecoae.gui.ldlib.state;

import net.minecraft.resources.ResourceLocation;

public record NEStorageUiTypeState(
        ResourceLocation typeId,
        String displayName,
        long usedTypes,
        long totalTypes,
        long usedBytes,
        long totalBytes,
        String usedTypesDisplay,
        String totalTypesDisplay,
        String usedBytesDisplay,
        String totalBytesDisplay,
        boolean infiniteTypesDisplay,
        boolean infiniteBytesDisplay) {
    public NEStorageUiTypeState(
            ResourceLocation typeId,
            String displayName,
            long usedTypes,
            long totalTypes,
            long usedBytes,
            long totalBytes,
            String usedTypesDisplay,
            String totalTypesDisplay,
            String usedBytesDisplay,
            String totalBytesDisplay) {
        this(
                typeId,
                displayName,
                usedTypes,
                totalTypes,
                usedBytes,
                totalBytes,
                usedTypesDisplay,
                totalTypesDisplay,
                usedBytesDisplay,
                totalBytesDisplay,
                false,
                false);
    }

    public NEStorageUiTypeState(
            ResourceLocation typeId,
            String displayName,
            long usedTypes,
            long totalTypes,
            long usedBytes,
            long totalBytes) {
        this(typeId, displayName, usedTypes, totalTypes, usedBytes, totalBytes, "", "", "", "", false, false);
    }
}
