package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.CombinedExactAmountSource;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOIOPortTransfer;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;

/** Moves storage contents in full-key batches using AE2 ME IO Port transfer semantics. */
final class ECOStorageInterfaceTransfer {
    private final ECOStorageSystemBlockEntity host;
    private final ECOIOPortTransfer ioPortTransfer = new ECOIOPortTransfer();
    private ECOInfiniteStorageEngine cachedStorageEngine;
    private MEStorage cachedInfiniteStorage;
    private CombinedStorage cachedCombinedStorage;

    ECOStorageInterfaceTransfer(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    void invalidateInfiniteStorageView() {
        ioPortTransfer.reset();
        cachedStorageEngine = null;
        cachedInfiniteStorage = null;
    }

    long transferStorageInterfaceContents(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        if (!host.isFormed() || !storageInterface.isStorageTransferMode() || !storageInterface.isTargetOnline()) {
            ioPortTransfer.reset();
            return 0L;
        }
        var grid = storageInterface.getMainNode().getGrid();
        if (grid == null) { ioPortTransfer.reset(); return 0L; }

        MEStorage hostStorage = getStorageInterfaceHostStorage();
        if (hostStorage == null) { ioPortTransfer.reset(); return 0L; }

        long moved = ioPortTransfer.transfer(
            grid,
            hostStorage,
            storageInterface.isStorageInputMode(),
            storageInterface.isIgnoringCreativeStorageInput(),
            storageInterface.isExcludingStorageBusContainers(),
            IActionSource.ofMachine(storageInterface),
            storageInterface.storageResourceFilter()
        );
        if (moved > 0L) {
            host.setChanged();
            host.markForUpdate();
        }
        return moved;
    }

    @Nullable
    MEStorage getStorageInterfaceHostStorage() {
        if (host.storageHostMode().isTransitioning()
            || host.storageHostMode().isInfiniteState()
                && (host.isInfiniteExitRequested() || !host.canUseHostDomainStorage())) {
            return null;
        }
        List<MEStorage> cells = new ArrayList<>();
        if (host.canUseHostDomainStorage()) {
            ECOInfiniteStorageEngine engine = host.getInfiniteEngine();
            if (engine != cachedStorageEngine) {
                cachedStorageEngine = engine;
                cachedInfiniteStorage = engine == null ? null : host.createInfiniteStorageView(engine);
            }
            if (cachedInfiniteStorage != null) cells.add(cachedInfiniteStorage);
        }
        if (host.getCluster() == null) return null;

        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            IECOStorageCell cell = drive.getCellInventory();
            if (cell != null
                && host.getTier().compareTo(cell.getTier()) >= 0
                && !host.isInfiniteMemberCell(drive.getCellStack())) {
                cells.add(cell);
            }
        }
        if (cells.isEmpty()) {
            cachedCombinedStorage = null;
            return null;
        }
        if (cachedCombinedStorage == null || !cachedCombinedStorage.hasInventories(cells)) {
            cachedCombinedStorage = new CombinedStorage(cells, host.getBlockState().getBlock().getName(),
                    () -> !host.storageHostMode().isInfiniteState() || host.canInsertIntoInfiniteDomain());
        }
        return cachedCombinedStorage;
    }

    static final class CombinedStorage implements MEStorage, CombinedExactAmountSource,
            cn.dancingsnow.neoecoae.impl.storage.StorageListingVersion {
        private final List<MEStorage> inventories;
        private final List<MEStorage> itemInserts;
        private final List<MEStorage> fluidInserts;
        private final net.minecraft.network.chat.Component description;
        private final BooleanSupplier allowAccess;
        private final Map<Object, Object> readers = new java.util.WeakHashMap<>();
        private final long[] versions;
        private long listingRevision;
        private boolean listingValid;

        CombinedStorage(List<MEStorage> inventories, net.minecraft.network.chat.Component description,
                BooleanSupplier allowAccess) {
            this.inventories = List.copyOf(inventories);
            this.description = description;
            this.allowAccess = allowAccess;
            versions = new long[inventories.size()];
            itemInserts = insertCandidates(appeng.api.stacks.AEKeyType.items());
            fluidInserts = insertCandidates(appeng.api.stacks.AEKeyType.fluids());
        }

        boolean hasInventories(List<MEStorage> current) {
            if (inventories.size() != current.size()) return false;
            for (int i = 0; i < inventories.size(); i++) if (inventories.get(i) != current.get(i)) return false;
            return true;
        }

        private List<MEStorage> insertCandidates(appeng.api.stacks.AEKeyType requested) {
            List<MEStorage> result = new ArrayList<>();
            for (MEStorage inventory : inventories) {
                if (inventory.getClass() == cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell.class) {
                    var type = ((cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell) inventory).getKeyType();
                    if ((type == appeng.api.stacks.AEKeyType.items() || type == appeng.api.stacks.AEKeyType.fluids())
                            && type != requested) continue;
                }
                result.add(inventory);
            }
            return result.size() == inventories.size() ? inventories : List.copyOf(result);
        }

        @Override public long neoecoae$listingVersion() { return getChangeVersion(); }

        @Override public long getChangeVersion() {
            if (!allowAccess.getAsBoolean()) { readers.clear(); listingValid = false; return -1; }
            boolean changed = !listingValid;
            for (int i = 0; i < inventories.size(); i++) {
                long version = cn.dancingsnow.neoecoae.impl.storage.StorageListingVersion.versionOf(inventories.get(i));
                if (version < 0) { listingValid = false; return -1; }
                changed |= versions[i] != version;
                versions[i] = version;
            }
            if (listingRevision == Long.MAX_VALUE) return -1;
            if (changed) listingRevision++;
            listingValid = true;
            return listingRevision;
        }

        @Override public boolean drainChanges(Object reader, java.util.function.ObjLongConsumer<AEKey> visitor) {
            if (reader == null) return false;
            if (!allowAccess.getAsBoolean() || inventories.size() != 1) { readers.clear(); return false; }
            // A wrapper owns its own subscription; sharing the caller's token would advance another view's cursor.
            Object token = readers.computeIfAbsent(reader, ignored -> new Object());
            return cn.dancingsnow.neoecoae.impl.storage.StorageListingVersion.drain(inventories.get(0), token, visitor);
        }

        CombinedStorage(List<MEStorage> inventories, net.minecraft.network.chat.Component description) {
            this(inventories, description, () -> true);
        }

        @Override
        public long insert(AEKey key, long amount, appeng.api.config.Actionable mode, IActionSource source) {
            if (!allowAccess.getAsBoolean()) return 0L;
            List<MEStorage> candidates = key instanceof appeng.api.stacks.AEItemKey ? itemInserts
                : key instanceof appeng.api.stacks.AEFluidKey ? fluidInserts : inventories;
            long inserted = 0L;
            for (int pass = 0; pass < 2 && inserted < amount; pass++) {
                for (MEStorage inventory : candidates) {
                    if (inserted >= amount) break;
                    boolean bulk = inventory instanceof IECOStorageCell cell && cell.prioritizesMarkedInserts();
                    if (bulk != (pass == 0)) continue;
                    inserted += inventory.insert(key, amount - inserted, mode, source);
                }
            }
            return inserted;
        }

        @Override
        public long extract(AEKey key, long amount, appeng.api.config.Actionable mode, IActionSource source) {
            if (!allowAccess.getAsBoolean()) return 0L;
            long extracted = 0L;
            for (MEStorage inventory : inventories) {
                if (extracted >= amount) break;
                extracted += inventory.extract(key, amount - extracted, mode, source);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (!allowAccess.getAsBoolean()) return;
            var accumulator = new cn.dancingsnow.neoecoae.crafting.display.terminal.StorageListingAccumulator();
            for (MEStorage inventory : inventories) {
                accumulator.append(inventory, out, inventory::getAvailableStacks);
            }
        }

        @Override
        public boolean neoecoae$hasSaturatingListing() { return true; }

        @Override
        public void neoecoae$listWithExactAmounts(KeyCounter out, BiConsumer<AEKey, ExactAmount> visitor) {
            if (!allowAccess.getAsBoolean()) return;
            Map<AEKey, ExactAmount> totals = new HashMap<>();
            for (MEStorage inventory : inventories) {
                KeyCounter contribution = new KeyCounter();
                if (inventory instanceof CombinedExactAmountSource source) {
                    source.neoecoae$listWithExactAmounts(contribution,
                        (key, amount) -> totals.merge(key, amount, ExactAmount::add));
                } else {
                    inventory.getAvailableStacks(contribution);
                    ExactAmountCollector.visitAmounts(inventory, contribution,
                        (key, amount) -> totals.merge(key, amount, ExactAmount::add));
                }
                SaturatingStackAccumulator.addAll(out, contribution);
            }
            totals.forEach(visitor);
        }

        @Override
        public void neoecoae$visitExactAmounts(BiConsumer<AEKey, ExactAmount> visitor) {
            neoecoae$listWithExactAmounts(new KeyCounter(), visitor);
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return description;
        }
    }
}
