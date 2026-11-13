package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import cn.dancingsnow.neoecoae.platform.nbt.NECellNbt;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import cn.dancingsnow.neoecoae.impl.storage.persistence.ECOStorageRepository;
import cn.dancingsnow.neoecoae.impl.storage.persistence.ECOCellReference;
import cn.dancingsnow.neoecoae.impl.storage.persistence.ECOCellSavedData;
import cn.dancingsnow.neoecoae.impl.storage.persistence.StorageAmounts;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
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
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOStorageCell implements IECOStorageMigrationCell,
        cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage,
        cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource, StorageListingVersion {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    @Nullable
    private final ISaveProvider container;
    private final IBasicECOCellItem cellType;
    @Getter
    private final AEKeyType keyType;
    // 过滤器
    @Getter
    private final IPartitionList partitionList;
    @Getter
    private final IncludeExclude partitionListMode;
    private final boolean hasVoidUpgrade;

    /** Protected so specialised cells (for example the infinite resource cell) can read the cell stack. */
    protected final ItemStack cellStack;

    private final int maxItemTypes;
    private final ECOStorageRepository.Access storageAccess;
    private final java.util.Map<Object, StorageChangeLog.Cursor> readerCursors = new java.util.WeakHashMap<>();
    public ECOCellSavedData storageData() { return storageAccess.data(); }
    public ECOStorageRepository.Session storageSession() { return storageAccess.session(); }
    public boolean storageWritable() { return storageAccess.writable(); }
    @Override public boolean isInfiniteStorageEligible() { return storageWritable(); }
    public long contentRevision() { return storageData().contentRevision(); }

    @Override
    public long neoecoae$listingVersion() {
        // Subclasses whose listing depends on other state must opt in by overriding this.
        return getClass() == ECOStorageCell.class ? contentListingVersion() : UNKNOWN;
    }

    /**
     * GTO's StorageService re-lists a mount only when this non-negative value changes; -1 means unknown and
     * is re-listed every refresh.
     */
    @Override
    public long getChangeVersion() {
        long version = neoecoae$listingVersion();
        return version < 0 ? -1L : version;
    }

    @Override
    public boolean drainChanges(Object reader, java.util.function.ObjLongConsumer<AEKey> visitor) {
        if (reader == null) return false;
        // Unknown subclasses and unreadable/sealed views cannot reuse an ordinary-disk baseline.
        if (getClass() != ECOStorageCell.class || contentListingVersion() < 0) {
            readerCursors.remove(reader);
            return false;
        }
        var quantities = storageData().amounts();
        var from = readerCursors.get(reader);
        boolean drained = from != null && quantities.visitListingChanges(from, visitor);
        readerCursors.put(reader, quantities.listingCursor());
        return drained;
    }

    /** For listings that are exactly {@link #getCellItems()} while the cell may be read. */
    protected final long contentListingVersion() {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)
                || !storageData().canRead() || !storageAccess.claimed() && storageAccess.session() != null) return -1L;
        return storageData().amounts().contentRevision();
    }
    public long getStoredItemCount() {
        return storageAccess.session() == null && ECOCellReference.id(cellStack) != null
            ? NEItemData.read(cellStack).getCompound(ECOCellReference.TAG).getLong("amount")
            : storageData().amounts().totalVisible();
    }
    public BigInteger getStoredItemCountExact() {
        if (storageAccess.session() == null && ECOCellReference.id(cellStack) != null) {
            var ref = NEItemData.read(cellStack).getCompound(ECOCellReference.TAG);
            return ref.contains("exactAmount") ? new BigInteger(ref.getString("exactAmount")) : BigInteger.valueOf(ref.getLong("amount"));
        }
        return storageData().amounts().totalExact();
    }
    private boolean isPersisted = true;
    private final Runnable persistenceNotification = () -> ECOCellMutationBatch.flush(this);
    @Getter
    private final IECOTier tier;

    public ECOStorageCell(ItemStack cellStack, @Nullable ISaveProvider container) {
        this(cellStack, container, true);
    }

    /** Stateless supplies must not acquire a persisted identity while being queried or mounted. */
    protected ECOStorageCell(ItemStack cellStack, @Nullable ISaveProvider container, boolean persistContents) {
        this.container = container;
        this.cellStack = cellStack;

        if (cellStack.getItem() instanceof IBasicECOCellItem c) {
            keyType = c.getKeyType();
            maxItemTypes = c.getTotalTypes();
            this.storageAccess = persistContents ? ECOStorageRepository.open(cellStack, container)
                : new ECOStorageRepository.Access(new ECOCellSavedData(new java.util.UUID(0, 0)), null, container, false);
            this.cellType = c;
            this.tier = c.getTier();

            // 根据已安装的升级和配置的过滤器更新分区列表和模式。
            var builder = IPartitionList.builder();

            var upgrades = getUpgradesInventory();
            var config = getConfigInventory();

            boolean hasInverter = upgrades.isInstalled(AEItems.INVERTER_CARD);
            boolean isFuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD);
            if (isFuzzy) {
                builder.fuzzyMode(c.getFuzzyMode(cellStack));
            }

            builder.addAll(config.keySet());

            partitionListMode = (hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
            partitionList = builder.build();

            this.hasVoidUpgrade = upgrades.isInstalled(AEItems.VOID_CARD);
        } else {
            throw new IllegalArgumentException("itemStack must be an ECOStorageCellItem");
        }
    }

    @Override
    public CellState getStatus() {
        if (!storageData().canMutate() || storageAccess.session() != null && !storageAccess.claimed()) return CellState.FULL;
        if (this.getStoredItemTypes() == 0) {
            return CellState.EMPTY;
        }
        if (this.canHoldNewItem()) {
            return CellState.NOT_EMPTY;
        }
        if (!hasInfiniteTypeCapacity() && this.getRemainingItemCount() > 0) {
            return CellState.TYPES_FULL;
        }
        return CellState.FULL;
    }

    public long getRemainingItemCount() {
        return NEMath.saturatingAdd(
            NEMath.saturatingMultiply(getFreeBytes(), Math.max(1L, keyType.getAmountPerByte())),
            getUnusedItemCount());
    }

    public long getFreeBytes() {
        return Math.max(0L, this.getTotalBytes() - this.getUsedBytes());
    }

    public int getUnusedItemCount() {
        int amountPerByte = Math.max(1, keyType.getAmountPerByte());
        var remainder = storageData().amounts().channelAmount(keyType).quantity()
            .remainder(PlannerAmount.of(amountPerByte));
        return remainder.isZero() ? 0 : amountPerByte - (int) remainder.longValueExact();
    }

    public int getBytesPerType() {
        return this.cellType.getBytesPerType();
    }

    public long getUsedBytes() {
        return PlannerAmount.of(getUsedBytesExact()).longValueSaturated();
    }

    public BigInteger getUsedBytesExact() {
        if (storageAccess.session() == null && ECOCellReference.id(cellStack) != null) {
            var ref = NEItemData.read(cellStack).getCompound(ECOCellReference.TAG);
            // Older references contain one channel only. Their UUID and authoritative contents stay unchanged.
            if (!ref.contains("contentBytes")) return StorageByteAccounting.usedBytes(
                getStoredItemTypes(), getStoredItemCountExact(), keyType.getAmountPerByte(), getBytesPerType());
            return new BigInteger(ref.getString("contentBytes")).add(BigInteger.valueOf(getStoredItemTypes())
                .multiply(BigInteger.valueOf(getBytesPerType())));
        }
        return storageData().amounts().usedBytes(getBytesPerType()).toBigInteger();
    }

    public long getTotalBytes() {
        return cellType.getBytes();
    }

    public long getTotalItemTypes() {
        return hasInfiniteTypeCapacity() ? Long.MAX_VALUE : this.maxItemTypes;
    }

    @Override
    public boolean hasInfiniteTypeCapacity() {
        return maxItemTypes == Integer.MAX_VALUE;
    }

    public long getRemainingItemTypes() {
        var basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        var baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();
        return Math.min(basedOnStorage, baseOnTotal);
    }

    private boolean canHoldNewItem() {
        final long bytesFree = this.getFreeBytes();
        if (bytesFree < getBytesPerType() || getRemainingItemTypes() <= 0) return false;
        if (bytesFree > getBytesPerType()) return true;
        for (AEKeyType type : cellType.getKeyTypes()) {
            var amount = storageData().amounts().channelAmount(type).quantity();
            if (!amount.remainder(PlannerAmount.of(Math.max(1, type.getAmountPerByte()))).isZero()) return true;
        }
        return false;
    }

    public long getStoredItemTypes() {
        return storageAccess.session() == null && ECOCellReference.id(cellStack) != null
            ? NEItemData.read(cellStack).getCompound(ECOCellReference.TAG).getInt("types")
            : storageData().amounts().size();
    }

    private List<GenericStack> getStoredStacks() {
        return NECellNbt.readStacks(cellStack);
    }

    public static boolean canStoreKeyInsideStorageCell(AEKey what) {
        if (what instanceof AEItemKey itemKey) {
            // Only probed, never mutated: skip the per-insert ItemStack copy.
            var cellInv = StorageCells.getCellInventory(itemKey.getReadOnlyStack(), null);
            return cellInv == null || cellInv.canFitInsideCell();
        }
        return true;
    }

    protected Reference2LongMap<AEKey> getCellItems() { return storageData().amounts().visibleAmounts(); }

    @Override
    public double getIdleDrain() {
        return cellType.getIdleDrain();
    }

    @Override
    public void persist() {
        if (storageAccess.session() == null) return;
        if (this.isPersisted && NEItemData.read(cellStack).getCompound(ECOCellReference.TAG)
                .getLong("revision") == storageData().contentRevision()) return;
        ECOCellReference.update(cellStack, storageData());
        this.isPersisted = true;
    }

    @Override
    public void persistMigrationContents(net.minecraft.server.level.ServerLevel level) {
        if (!storageWritable()) throw new IllegalStateException("ECO disk is unavailable or mounted twice");
        storageData().metadata(ECOCellReference.metadata(cellStack));
        if (!storageData().flush()) throw new IllegalStateException("Cannot persist ECO migration: " + storageData().failure());
        this.isPersisted = false;
        persist();
    }

    protected void saveChanges() {
        storageData().setDirty();
        this.isPersisted = false;
        if (ECOCellMutationBatch.defer(this)) return;
        if (storageAccess.session() != null) storageAccess.session().schedule(cellStack, persistenceNotification);
    }

    void flushBatchedChanges() {
        this.persist();
        if (this.container != null) this.container.saveChanges();
    }

    /** Registered channels are accepted independently of the legacy item/fluid disk identity. */
    protected boolean acceptsKey(AEKey key) {
        return hasInfiniteTypeCapacity() || cellType.getKeyTypes().contains(key.getType());
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return 0L;
        if (!storageWritable() || amount <= 0 || what == null || !acceptsKey(what)) {
            return 0;
        }

        if (!this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }

        if (this.cellType.isBlackListed(cellStack, what)) {
            return 0;
        }

        // 执行常规插入逻辑，然后对返回值应用虚空升级效果。
        long inserted = innerInsert(what, amount, mode);

        // 当虚空卡用于（已满的）未格式化元件时，确保不会虚空掉元件未存储且无法存储的物品。
        if (partitionList.isEmpty() && hasVoidUpgrade && !canHoldNewItem()) {
            return getCellItems().containsKey(what) ? amount : inserted;
        }

        return hasVoidUpgrade ? amount : inserted;
    }

    /** Inserts for a lossless migration without applying the void upgrade's reported acceptance. */
    public long insertForMigration(AEKey what, long amount, Actionable mode) {
        if (!storageWritable() || amount <= 0 || what == null || !acceptsKey(what)) {
            return 0;
        }
        if (!partitionList.matchesFilter(what, partitionListMode) || cellType.isBlackListed(cellStack, what)) {
            return 0;
        }
        return innerInsert(what, amount, mode);
    }

    /** Marks an in-memory mutation from a specialised cell implementation. */
    protected final void markContentChanged() {
        storageData().setDirty();
    }

    /** Joins the current controller mutation batch, if one is active. */
    protected final boolean deferMutationBatch() {
        return ECOCellMutationBatch.defer(this);
    }

    @Override
    public long insertForMigration(AEKey what, long amount, Actionable mode, IActionSource source) {
        return insertForMigration(what, amount, mode);
    }

    /**
     * Computes migration capacity against a caller-owned inventory snapshot. A restore preflight must not mutate a
     * real cell because some cell implementations back copied ItemStacks with world-level storage.
     */
    public long simulateInsertForMigration(AEKey what, long amount, KeyCounter simulatedContents) {
        if (simulatedContents == null || what == null || amount <= 0) return 0L;

        var contents = new StorageAmounts();
        for (var entry : simulatedContents) {
            if (entry.getLongValue() > 0) contents.setLong(entry.getKey(), entry.getLongValue());
        }
        return simulateInsertForMigrationQuantity(what, PlannerAmount.of(amount), contents.get(what).quantity(),
            contents.size(), contents.usedBytes(getBytesPerType()), contents.channelAmount(what.getType()).quantity()).longValueSaturated();
    }

    @Override
    public long simulateInsertForMigration(
        AEKey what,
        long amount,
        KeyCounter simulatedContents,
        long simulatedTypes,
        long simulatedAmount
    ) {
        return simulateInsertForMigration(what, amount, simulatedContents);
    }

    @Override
    public long getUsedBytesForMigration(KeyCounter simulatedContents) {
        if (simulatedContents == null) {
            return 0L;
        }
        var contents = new StorageAmounts();
        for (var entry : simulatedContents) {
            if (entry.getLongValue() > 0) contents.setLong(entry.getKey(), entry.getLongValue());
        }
        return contents.usedBytes(getBytesPerType()).longValueSaturated();
    }

    private long innerInsert(AEKey what, long amount, Actionable mode) {
        var amounts = storageData().amounts();
        long acceptable = simulateInsertForMigrationQuantity(what, PlannerAmount.of(amount), amounts.get(what).quantity(),
            amounts.size(), amounts.usedBytes(getBytesPerType()), amounts.channelAmount(what.getType()).quantity()).longValueSaturated();
        amount = Math.min(amount, acceptable);
        if (amount <= 0) return 0;

        if (mode == Actionable.MODULATE) {
            storageData().amounts().add(what, amount);
            this.saveChanges();
        }

        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (what == null || !storageWritable() || amount <= 0L || cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return 0L;
        var quantities = storageData().amounts();
        if (mode == Actionable.SIMULATE) return Math.min(amount, quantities.visible(what));
        long extracted = quantities.extract(what, amount);
        if (extracted > 0) saveChanges();
        return extracted;
    }

    @Override
    public boolean canFitInsideCell() {
        return !cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)
            && storageData().canMutate() && getStoredItemTypes() == 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return;
        if (!storageData().canRead() || !storageAccess.claimed() && storageAccess.session() != null) return;
        getMigrationStacks(out);
    }

    @Override
    public void getMigrationStacks(KeyCounter out) {
        // The table's own fast iterator: the read-only view cannot hand one out and would allocate an entry per key.
        var amounts = storageData().amounts();
        if (out.isEmpty()) {
            // Keys are unique within the table, so a fresh counter needs one write per key and no merge.
            amounts.visitVisible(out::set);
            return;
        }
        amounts.visitVisible((key, value) -> {
            long existing = out.get(key);
            out.set(key, existing <= 0 ? value : NEMath.saturatingAdd(existing, value));
        });
    }

    @Override
    public java.util.Iterator<? extends java.util.Map.Entry<AEKey, Long>> migrationEntries() {
        return getCellItems().reference2LongEntrySet().iterator();
    }

    @Override
    public long getMigrationAmount(AEKey key) { return getCellItems().getLong(key); }

    @Override public BigInteger getMigrationAmountBigInteger(AEKey key) { return storageData().amounts().get(key).toBigInteger(); }

    /** Capacity of a mixed-channel snapshot; the bucket is the total of the offered key's channel. */
    public PlannerAmount simulateInsertForMigrationQuantity(AEKey key, PlannerAmount offered,
            PlannerAmount current, long types, PlannerAmount usedBytes, PlannerAmount channelAmount) {
        if (key == null || offered == null || offered.signum() <= 0 || !acceptsKey(key)
            || !partitionList.matchesFilter(key, partitionListMode) || cellType.isBlackListed(cellStack, key)) return PlannerAmount.ZERO;
        if (current.signum() == 0 && !canStoreKeyInsideStorageCell(key)) return PlannerAmount.ZERO;
        PlannerAmount free = PlannerAmount.of(getTotalBytes()).subtract(usedBytes);
        if (free.signum() < 0) return PlannerAmount.ZERO;
        PlannerAmount perByte = PlannerAmount.of(Math.max(1, key.getType().getAmountPerByte()));
        PlannerAmount remainder = channelAmount.remainder(perByte);
        PlannerAmount unused = remainder.isZero() ? PlannerAmount.ZERO : perByte.subtract(remainder);
        if (current.signum() == 0) {
            if (!hasInfiniteTypeCapacity() && types >= getTotalItemTypes()) return PlannerAmount.ZERO;
            free = free.subtract(Math.max(0, getBytesPerType()));
            if (free.signum() < 0) return PlannerAmount.ZERO;
        }
        return offered.min(free.multiply(perByte).add(unused));
    }

    private BigInteger insertExact(AEKey key, BigInteger amount, Actionable mode) {
        BigInteger current = getMigrationAmountBigInteger(key);
        var amounts = storageData().amounts();
        BigInteger inserted = simulateInsertForMigrationQuantity(key, PlannerAmount.of(amount), PlannerAmount.of(current),
            amounts.size(), amounts.usedBytes(getBytesPerType()), amounts.channelAmount(key.getType()).quantity()).toBigInteger();
        if (mode == Actionable.MODULATE && inserted.signum() > 0) {
            storageData().amounts().set(key, HugeAmount.of(current).add(HugeAmount.of(inserted))); saveChanges();
        }
        return inserted;
    }

    @Override public BigInteger insertBigInteger(AEKey key, BigInteger amount, Actionable mode, IActionSource source) {
        if (!storageWritable() || key == null || amount == null || amount.signum() <= 0
            || cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)
            || !acceptsKey(key) || !partitionList.matchesFilter(key, partitionListMode)
            || cellType.isBlackListed(cellStack, key)) return BigInteger.ZERO;
        if (amount.compareTo(MAX_LONG) <= 0 && getStoredItemCount() < Long.MAX_VALUE)
            return BigInteger.valueOf(insert(key, amount.longValueExact(), mode, source));
        BigInteger inserted = insertExact(key, amount, mode);
        if (!hasVoidUpgrade) return inserted;
        return partitionList.isEmpty() && !canHoldNewItem() && getMigrationAmountBigInteger(key).signum() == 0 ? inserted : amount;
    }

    @Override public BigInteger insertForMigrationBigInteger(AEKey key, BigInteger amount, Actionable mode, IActionSource source) {
        if (!storageWritable() || key == null || amount == null || amount.signum() <= 0) return BigInteger.ZERO;
        return insertExact(key, amount, mode);
    }

    @Override public BigInteger extractBigInteger(AEKey key, BigInteger amount, Actionable mode, IActionSource source) {
        if (!storageWritable() || key == null || amount == null || amount.signum() <= 0
            || cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return BigInteger.ZERO;
        HugeAmount current = storageData().amounts().get(key), extracted = current.min(HugeAmount.of(amount));
        if (mode == Actionable.MODULATE && !extracted.isZero()) {
            storageData().amounts().set(key, current.subtract(extracted)); saveChanges();
        }
        return extracted.toBigInteger();
    }

    @Override public void neoecoae$visitExactAmounts(java.util.function.BiConsumer<AEKey,
            cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> visitor) {
        if (!storageData().canRead() || storageAccess.session() != null && !storageAccess.claimed()
            || cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return;
        storageData().amounts().visitExact((key, amount) -> visitor.accept(key,
            cn.dancingsnow.neoecoae.crafting.amount.ExactAmount.finite(amount)));
    }

    @Override
    public Component getDescription() {
        return cellStack.getHoverName();
    }

    @Override
    public boolean supportsKeyType(AEKeyType<?> type) {
        return cellType.getKeyTypes().contains(type);
    }

    @Override
    public int operationTime() {
        return 1;
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

    public void clearAllStoredStacks() {
        if (!storageWritable()) throw new IllegalStateException("Cannot clear unavailable ECO disk");
        if (storageData().amounts().isEmpty()) return;
        storageData().amounts().clear();
        saveChanges();
    }

    @Override
    public void clearMigrationStacks() {
        clearAllStoredStacks();
    }

}
