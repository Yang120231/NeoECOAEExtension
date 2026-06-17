package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ECOStorageCellMetadata {
    public static final String LEGACY_CONTENTS = "eco_cell_contents";

    private static final String DISK_ID = "neoecoae_disk_id";
    private static final String MODE = "neoecoae_storage_mode";
    private static final String HOST_DOMAIN_ID = "neoecoae_host_domain_id";
    private static final String MEMBER_INDEX = "neoecoae_member_index";
    private static final String SUMMARY_STORED_TYPES = "neoecoae_summary_stored_types";
    private static final String SUMMARY_STORED_COUNT = "neoecoae_summary_stored_count";
    private static final String SUMMARY_USED_BYTES = "neoecoae_summary_used_bytes";
    private static final String SUMMARY_TOTAL_BYTES = "neoecoae_summary_total_bytes";
    private static final String LEGACY_INFINITE_LOCK = "neo_infiniteStorageMatrixLocked";

    private ECOStorageCellMetadata() {}

    public static @Nullable UUID getDiskId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DISK_ID, Tag.TAG_STRING)) {
            return null;
        }
        return readUuid(tag.getString(DISK_ID));
    }

    public static UUID getOrCreateDiskId(ItemStack stack) {
        UUID diskId = getDiskId(stack);
        if (diskId != null) {
            return diskId;
        }
        UUID created = UUID.randomUUID();
        stack.getOrCreateTag().putString(DISK_ID, created.toString());
        if (!stack.getOrCreateTag().contains(MODE, Tag.TAG_STRING)) {
            setMode(stack, ECOStorageCellMode.PORTABLE);
        }
        return created;
    }

    public static ECOStorageCellMode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return ECOStorageCellMode.PORTABLE;
        }
        return ECOStorageCellMode.byName(tag.getString(MODE));
    }

    public static void setMode(ItemStack stack, ECOStorageCellMode mode) {
        stack.getOrCreateTag().putString(MODE, mode.name());
    }

    public static boolean isPortable(ItemStack stack) {
        return getMode(stack) == ECOStorageCellMode.PORTABLE && !isLegacyInfiniteLocked(stack);
    }

    public static boolean isMigrating(ItemStack stack) {
        return getMode(stack) == ECOStorageCellMode.MIGRATING;
    }

    public static boolean isDomainMember(ItemStack stack) {
        return getMode(stack) == ECOStorageCellMode.DOMAIN_MEMBER;
    }

    public static boolean isLegacyInfiniteLocked(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(LEGACY_INFINITE_LOCK);
    }

    public static boolean hasNonPortableState(ItemStack stack) {
        ECOStorageCellMode mode = getMode(stack);
        return mode == ECOStorageCellMode.MIGRATING || mode == ECOStorageCellMode.DOMAIN_MEMBER;
    }

    public static @Nullable UUID getHostDomainId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(HOST_DOMAIN_ID, Tag.TAG_STRING)) {
            return null;
        }
        return readUuid(tag.getString(HOST_DOMAIN_ID));
    }

    public static void markMigrating(ItemStack stack, UUID hostDomainId, int memberIndex) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(HOST_DOMAIN_ID, hostDomainId.toString());
        tag.putInt(MEMBER_INDEX, memberIndex);
        tag.putBoolean(LEGACY_INFINITE_LOCK, true);
        setMode(stack, ECOStorageCellMode.MIGRATING);
    }

    public static void markDomainMember(ItemStack stack, UUID hostDomainId, int memberIndex) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(HOST_DOMAIN_ID, hostDomainId.toString());
        tag.putInt(MEMBER_INDEX, memberIndex);
        tag.putBoolean(LEGACY_INFINITE_LOCK, true);
        setMode(stack, ECOStorageCellMode.DOMAIN_MEMBER);
    }

    public static void clearDomainBinding(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(HOST_DOMAIN_ID);
        tag.remove(MEMBER_INDEX);
        tag.remove(LEGACY_INFINITE_LOCK);
        setMode(stack, ECOStorageCellMode.PORTABLE);
        writeSummary(stack, 0L, 0L, 0L, 0L);
    }

    public static int getMemberIndex(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? -1 : tag.getInt(MEMBER_INDEX);
    }

    public static List<GenericStack> readLegacyStacks(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(LEGACY_CONTENTS, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = tag.getList(LEGACY_CONTENTS, Tag.TAG_COMPOUND);
        List<GenericStack> stacks = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            GenericStack stackEntry = GenericStack.readTag(list.getCompound(i));
            if (stackEntry != null && stackEntry.amount() > 0) {
                stacks.add(stackEntry);
            }
        }
        return stacks;
    }

    public static void clearLegacyStacks(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(LEGACY_CONTENTS);
        }
    }

    public static void writeSummary(
            ItemStack stack, long storedTypes, long storedCount, long usedBytes, long totalBytes) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(SUMMARY_STORED_TYPES, storedTypes);
        tag.putLong(SUMMARY_STORED_COUNT, storedCount);
        tag.putLong(SUMMARY_USED_BYTES, usedBytes);
        tag.putLong(SUMMARY_TOTAL_BYTES, totalBytes);
    }

    public static long getSummaryStoredTypes(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0L : tag.getLong(SUMMARY_STORED_TYPES);
    }

    public static long getSummaryStoredCount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0L : tag.getLong(SUMMARY_STORED_COUNT);
    }

    public static long getSummaryUsedBytes(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0L : tag.getLong(SUMMARY_USED_BYTES);
    }

    public static long getSummaryTotalBytes(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0L : tag.getLong(SUMMARY_TOTAL_BYTES);
    }

    private static @Nullable UUID readUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
