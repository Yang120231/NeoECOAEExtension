package cn.dancingsnow.neoecoae.compat.jade.provider;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellMetadata;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellMode;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECODriveProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.getBoolean("infiniteMember")) {
            tooltip.add(Component.translatable("jade.neoecoae.drive_infinite")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (data.getBoolean("migratingToInfinite")) {
            tooltip.add(Component.translatable("jade.neoecoae.drive_migrating_to_infinite")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        if (data.contains("mounted")) {
            boolean mounted = data.getBoolean("mounted");
            if (mounted) {
                tooltip.add(
                        Component.translatable("jade.neoecoae.drive_mounted").withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add(
                        Component.translatable("jade.neoecoae.drive_unmounted").withStyle(ChatFormatting.RED));
                return;
            }
        }
        if (data.contains("usedBytes") && data.contains("totalBytes")) {
            tooltip.add(Tooltips.bytesUsed(data.getLong("usedBytes"), data.getLong("totalBytes")));
        }
        if (data.contains("storedItemTypes") && data.contains("totalItemTypes")) {
            long storedItemTypes = data.getLong("storedItemTypes");
            long totalItemTypes = data.getLong("totalItemTypes");
            if (storedItemTypes == Long.MAX_VALUE || totalItemTypes == Long.MAX_VALUE) {
                tooltip.add(Component.literal("Types: " + NELDLibText.typeCount(storedItemTypes) + " / "
                                + NELDLibText.typeCount(totalItemTypes))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Tooltips.typesUsed(storedItemTypes, totalItemTypes));
            }
        }
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECODriveBlockEntity drive) {
            ItemStack stack = drive.getCellStack();
            if (stack != null && !stack.isEmpty()) {
                ECOStorageCellMode mode = ECOStorageCellMetadata.getMode(stack);
                tag.putBoolean("infiniteMember", mode == ECOStorageCellMode.DOMAIN_MEMBER);
                tag.putBoolean("migratingToInfinite", mode == ECOStorageCellMode.MIGRATING);
            }
            tag.putBoolean("mounted", drive.isMounted());
            IECOStorageCell cellInventory = drive.getCellInventory();
            if (cellInventory != null) {
                tag.putLong("usedBytes", cellInventory.getUsedBytes());
                tag.putLong("totalBytes", cellInventory.getTotalBytes());
                tag.putLong("storedItemTypes", cellInventory.getStoredItemTypes());
                tag.putLong("totalItemTypes", cellInventory.getTotalItemTypes());
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_drive");
    }
}
