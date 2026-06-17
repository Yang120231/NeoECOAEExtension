package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.IPartitionList;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.api.storage.IBatchedECOCellSaveProvider;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellHostContext;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.mojang.logging.LogUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ECOStorageCell implements IECOStorageCell {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @Nullable private final ISaveProvider container;

    @Nullable private final ECOStorageSavedData savedData;

    @Nullable private final UUID diskId;

    private final IBasicECOCellItem cellType;

    @Getter
    private final AEKeyType keyType;

    @Getter
    private final IPartitionList partitionList;

    @Getter
    private final IncludeExclude partitionListMode;

    private final boolean hasVoidUpgrade;
    private final ItemStack cellStack;
    private final int maxItemTypes;
    private final boolean worldBacked;
    private final boolean summaryOnly;

    private int storedItems;

    @Getter
    private long storedItemCount;

    private final Map<AEKey, BigInteger> storedAmounts;
    private boolean isPersisted = true;

    @Getter
    private final IECOTier tier;

    public ECOStorageCell(ItemStack cellStack, @Nullable ISaveProvider container) {
        this.container = container;
        this.cellStack = cellStack;

        if (!(cellStack.getItem() instanceof IBasicECOCellItem c)) {
            throw new IllegalArgumentException("itemStack must be an ECOStorageCellItem");
        }

        keyType = c.getKeyType();
        maxItemTypes = c.getTotalTypes();
        this.cellType = c;
        this.tier = c.getTier();

        ServerLevel storageLevel = null;
        if (container instanceof IECOStorageCellHostContext context) {
            storageLevel = context.getStorageLevel();
        }

        if (storageLevel != null && ECOStorageCellMetadata.getMode(cellStack) == ECOStorageCellMode.PORTABLE) {
            UUID id = ECOStorageCellMetadata.getOrCreateDiskId(cellStack);
            ECOStorageSavedData data = ECOStorageSavedData.get(storageLevel);
            data.importLegacyDiskContents(id, ECOStorageCellMetadata.readLegacyStacks(cellStack));
            ECOStorageCellMetadata.clearLegacyStacks(cellStack);
            this.savedData = data;
            this.diskId = id;
            this.storedAmounts = data.getOrCreateDisk(id).amounts();
            this.worldBacked = true;
            this.summaryOnly = false;
        } else {
            this.savedData = null;
            this.diskId = ECOStorageCellMetadata.getDiskId(cellStack);
            this.storedAmounts = readLegacyContents(cellStack);
            this.worldBacked = false;
            this.summaryOnly = storedAmounts.isEmpty()
                    && ECOStorageCellMetadata.getSummaryStoredTypes(cellStack) > 0
                    && ECOStorageCellMetadata.getMode(cellStack) == ECOStorageCellMode.PORTABLE;
        }

        refreshCachedCounts();
        if (summaryOnly) {
            this.storedItems =
                    (int) Math.min(Integer.MAX_VALUE, ECOStorageCellMetadata.getSummaryStoredTypes(cellStack));
            this.storedItemCount = ECOStorageCellMetadata.getSummaryStoredCount(cellStack);
        } else if (worldBacked) {
            ECOStorageCellMetadata.writeSummary(
                    cellStack, getStoredItemTypes(), getStoredItemCount(), getUsedBytes(), getTotalBytes());
            if (container != null) {
                container.saveChanges();
            }
        }

        var builder = IPartitionList.builder();
        var upgrades = getUpgradesInventory();
        var config = getConfigInventory();

        boolean hasInverter = upgrades.isInstalled(AEItems.INVERTER_CARD);
        boolean isFuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD);
        if (isFuzzy) {
            builder.fuzzyMode(c.getFuzzyMode(cellStack));
        }

        builder.addAll(config.keySet());

        partitionListMode = hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        partitionList = builder.build();
        this.hasVoidUpgrade = upgrades.isInstalled(AEItems.VOID_CARD);
    }

    @Override
    public CellState getStatus() {
        if (this.getStoredItemTypes() == 0) {
            return CellState.EMPTY;
        }
        if (this.canHoldNewItem()) {
            return CellState.NOT_EMPTY;
        }
        if (this.getRemainingItemCount() > 0) {
            return CellState.TYPES_FULL;
        }
        return CellState.FULL;
    }

    public long getRemainingItemCount() {
        final long remaining = saturatedAdd(
                saturatedMultiply(this.getFreeBytes(), keyType.getAmountPerByte()), this.getUnusedItemCount());
        return remaining > 0 ? remaining : 0;
    }

    public long getFreeBytes() {
        return Math.max(0L, this.getTotalBytes() - this.getUsedBytes());
    }

    public int getUnusedItemCount() {
        final int div = (int) (this.getStoredItemCount() % keyType.getAmountPerByte());

        if (div == 0) {
            return 0;
        }

        return keyType.getAmountPerByte() - div;
    }

    public int getBytesPerType() {
        return this.cellType.getBytesPerType();
    }

    public long getUsedBytes() {
        if (summaryOnly) {
            return ECOStorageCellMetadata.getSummaryUsedBytes(cellStack);
        }
        BigInteger itemCount = totalAmount();
        BigInteger unused = BigInteger.valueOf(unusedItemCount(itemCount));
        BigInteger bytesForItems = itemCount.add(unused).divide(BigInteger.valueOf(keyType.getAmountPerByte()));
        BigInteger bytesForTypes =
                BigInteger.valueOf(getStoredItemTypes()).multiply(BigInteger.valueOf(getBytesPerType()));
        return ECOStorageSavedData.saturate(bytesForTypes.add(bytesForItems));
    }

    public long getTotalBytes() {
        if (summaryOnly) {
            long summary = ECOStorageCellMetadata.getSummaryTotalBytes(cellStack);
            return summary > 0 ? summary : cellType.getBytes();
        }
        return cellType.getBytes();
    }

    public long getTotalItemTypes() {
        return Long.MAX_VALUE;
    }

    public long getRemainingItemTypes() {
        var basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        var baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();
        return Math.min(basedOnStorage, baseOnTotal);
    }

    private boolean canHoldNewItem() {
        final long bytesFree = this.getFreeBytes();
        return (bytesFree > this.getBytesPerType()
                        || bytesFree == this.getBytesPerType() && this.getUnusedItemCount() > 0)
                && this.getRemainingItemTypes() > 0;
    }

    public long getStoredItemTypes() {
        return storedItems;
    }

    public List<GenericStack> getStoredStacks() {
        if (summaryOnly) {
            return List.of();
        }
        List<GenericStack> stacks = new ArrayList<>(storedAmounts.size());
        for (Map.Entry<AEKey, BigInteger> entry : storedAmounts.entrySet()) {
            long amount = ECOStorageSavedData.saturate(entry.getValue());
            if (amount > 0) {
                stacks.add(new GenericStack(entry.getKey(), amount));
            }
        }
        return stacks;
    }

    @Override
    public double getIdleDrain() {
        return (double) cellType.getIdleDrainBytes() / (1 << 20);
    }

    @Override
    public void persist() {
        if (this.isPersisted) {
            return;
        }

        refreshCachedCounts();
        if (worldBacked) {
            if (savedData != null) {
                savedData.setDirty();
            }
            ECOStorageCellMetadata.clearLegacyStacks(cellStack);
        } else {
            writeLegacyContents();
        }

        int actualTypes = this.storedAmounts.size();
        if (maxItemTypes != Integer.MAX_VALUE && actualTypes > this.maxItemTypes) {
            LOGGER.warn(
                    "ECO storage cell contains more types than allowed: actual={} max={} stack={}",
                    actualTypes,
                    this.maxItemTypes,
                    cellStack);
        }

        ECOStorageCellMetadata.writeSummary(
                cellStack, getStoredItemTypes(), getStoredItemCount(), getUsedBytes(), getTotalBytes());
        this.isPersisted = true;
    }

    protected void saveChanges() {
        this.isPersisted = false;
        if (this.container == null) {
            this.persist();
        } else if (this.container instanceof IBatchedECOCellSaveProvider) {
            this.container.saveChanges();
        } else {
            this.persist();
            this.container.saveChanges();
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !keyType.contains(what) || summaryOnly) {
            return 0;
        }

        if (!this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }

        if (this.cellType.isBlackListed(cellStack, what)) {
            return 0;
        }

        long inserted = innerInsert(what, amount, mode);

        if (partitionList.isEmpty() && hasVoidUpgrade && !canHoldNewItem()) {
            return storedAmounts.containsKey(what) ? amount : inserted;
        }

        return hasVoidUpgrade ? amount : inserted;
    }

    private long innerInsert(AEKey what, long amount, Actionable mode) {
        if (what instanceof AEItemKey itemKey) {
            var stack = itemKey.toStack();

            var cellInv = StorageCells.getCellInventory(stack, null);
            if (cellInv != null && !cellInv.canFitInsideCell()) {
                return 0;
            }
        }

        BigInteger currentAmount = getStoredAmount(what);
        long remainingItemCount = this.getRemainingItemCount();

        if (currentAmount.signum() <= 0) {
            if (!canHoldNewItem()) {
                return 0;
            }

            remainingItemCount -= (long) this.getBytesPerType() * keyType.getAmountPerByte();
            if (remainingItemCount <= 0) {
                return 0;
            }
        }

        remainingItemCount = Math.max(0, remainingItemCount);

        if (amount > remainingItemCount) {
            amount = remainingItemCount;
        }

        if (mode == Actionable.MODULATE && amount > 0) {
            setStoredAmount(what, currentAmount.add(BigInteger.valueOf(amount)));
            this.saveChanges();
        }

        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (summaryOnly) {
            return 0L;
        }
        BigInteger currentAmount = getStoredAmount(what);
        if (currentAmount.signum() > 0) {
            long extracted = currentAmount.compareTo(BigInteger.valueOf(amount)) > 0
                    ? amount
                    : ECOStorageSavedData.saturate(currentAmount);
            if (mode == Actionable.MODULATE && extracted > 0) {
                setStoredAmount(what, currentAmount.subtract(BigInteger.valueOf(extracted)));
                this.saveChanges();
            }

            return extracted;
        }

        return 0;
    }

    @Override
    public boolean canFitInsideCell() {
        return getStoredItemTypes() == 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (summaryOnly) {
            return;
        }
        for (Map.Entry<AEKey, BigInteger> entry : storedAmounts.entrySet()) {
            long amount = ECOStorageSavedData.saturate(entry.getValue());
            if (amount > 0) {
                out.add(entry.getKey(), amount);
            }
        }
    }

    @Override
    public Component getDescription() {
        return cellStack.getHoverName();
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        if (summaryOnly) {
            return false;
        }
        boolean used = !this.storedAmounts.isEmpty() && this.insert(what, 1, Actionable.SIMULATE, source) == 1;
        boolean sameItem = this.extract(what, 1, Actionable.SIMULATE, source) > 0;
        return used || sameItem;
    }

    @Override
    public ECOCellType getCellType() {
        return ((ECOStorageCellItem) cellStack.getItem()).getCellType();
    }

    public IUpgradeInventory getUpgradesInventory() {
        return ((ECOStorageCellItem) cellStack.getItem()).getUpgrades(cellStack);
    }

    public ConfigInventory getConfigInventory() {
        return ((ECOStorageCellItem) cellStack.getItem()).getConfigInventory(cellStack);
    }

    public @Nullable UUID getDiskId() {
        return diskId;
    }

    private BigInteger getStoredAmount(AEKey key) {
        return storedAmounts.getOrDefault(key, BigInteger.ZERO);
    }

    private void setStoredAmount(AEKey key, BigInteger amount) {
        if (amount.signum() <= 0) {
            storedAmounts.remove(key);
        } else {
            storedAmounts.put(key, amount);
        }
        refreshCachedCounts();
    }

    private void refreshCachedCounts() {
        int types = 0;
        BigInteger total = BigInteger.ZERO;
        storedAmounts.entrySet().removeIf(entry -> entry.getValue().signum() <= 0);
        for (BigInteger amount : storedAmounts.values()) {
            if (amount.signum() > 0) {
                types++;
                total = total.add(amount);
            }
        }
        this.storedItems = types;
        this.storedItemCount = total.compareTo(LONG_MAX) >= 0 ? Long.MAX_VALUE : total.longValue();
    }

    private BigInteger totalAmount() {
        BigInteger total = BigInteger.ZERO;
        for (BigInteger amount : storedAmounts.values()) {
            total = total.add(amount);
        }
        return total;
    }

    private int unusedItemCount(BigInteger itemCount) {
        int amountPerByte = keyType.getAmountPerByte();
        int div = itemCount.mod(BigInteger.valueOf(amountPerByte)).intValue();
        return div == 0 ? 0 : amountPerByte - div;
    }

    private Map<AEKey, BigInteger> readLegacyContents(ItemStack stack) {
        Map<AEKey, BigInteger> amounts = new HashMap<>();
        for (GenericStack storedStack : ECOStorageCellMetadata.readLegacyStacks(stack)) {
            amounts.put(storedStack.what(), BigInteger.valueOf(storedStack.amount()));
        }
        return amounts;
    }

    private void writeLegacyContents() {
        ListTag list = new ListTag();
        for (Map.Entry<AEKey, BigInteger> entry : storedAmounts.entrySet()) {
            long amount = ECOStorageSavedData.saturate(entry.getValue());
            if (amount > 0) {
                list.add(GenericStack.writeTag(new GenericStack(entry.getKey(), amount)));
            }
        }
        cellStack.getOrCreateTag().put(ECOStorageCellMetadata.LEGACY_CONTENTS, list);
    }

    private static long saturatedAdd(long left, long right) {
        if (left == Long.MAX_VALUE || right == Long.MAX_VALUE || right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
