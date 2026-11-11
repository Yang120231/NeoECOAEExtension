package cn.dancingsnow.neoecoae.impl.storage.transfer;

import java.util.function.Predicate;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import cn.dancingsnow.neoecoae.impl.storage.StorageInterfaceFilter;
import cn.dancingsnow.neoecoae.impl.storage.StorageListingVersion;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;

/** Full-key transfer with the ordinary branch's candidate reuse and GTO's native change journal. */
public final class ECOIOPortTransfer {
    private static final int KEYS_PER_TICK = 5;
    private static final StorageInterfaceFilter ALL = new StorageInterfaceFilter(new net.minecraft.nbt.CompoundTag());
    private final ObjectLinkedOpenHashSet<AEKey> queue = new ObjectLinkedOpenHashSet<>();
    private final KeyCounter outputListing = new KeyCounter();
    private MEStorage previousSource;
    private MEStorage previousDestination;
    private Predicate<AEKey> previousFilter;
    private long listedVersion = -1;
    private boolean listedBusScope;
    private boolean transferring;

    /** Only candidate keys are buffered. Stock remains in the source inventory. */
    public void reset() {
        previousSource = previousDestination = null;
        previousFilter = null;
        listedVersion = -1;
        outputListing.clear();
        queue.clear();
    }

    public long transfer(IGrid grid, MEStorage hostStorage, boolean fillHost, IActionSource source) {
        return transfer(grid, hostStorage, fillHost, false, source);
    }

    public long transfer(IGrid grid, MEStorage hostStorage, boolean fillHost,
                         boolean ignoreCreativeInput, IActionSource source) {
        return transfer(grid, hostStorage, fillHost, ignoreCreativeInput, source, ALL);
    }

    public long transfer(IGrid grid, MEStorage hostStorage, boolean fillHost,
                         boolean ignoreCreativeInput, IActionSource source, Predicate<AEKey> filter) {
        return transfer(grid, hostStorage, fillHost, ignoreCreativeInput, true, source, filter);
    }

    public long transfer(IGrid grid, MEStorage hostStorage, boolean fillHost,
                         boolean ignoreCreativeInput, boolean excludeStorageBuses,
                         IActionSource source, Predicate<AEKey> filter) {
        if (transferring) return 0;
        transferring = true;
        try {
            return StorageBusTransferFilter.transfer(excludeStorageBuses,
                () -> transferContents(grid, hostStorage, fillHost, ignoreCreativeInput, excludeStorageBuses, source, filter));
        } catch (RuntimeException | Error failure) {
            reset();
            throw failure;
        } finally {
            transferring = false;
        }
    }

    private long transferContents(IGrid grid, MEStorage hostStorage, boolean fillHost,
                                  boolean ignoreCreativeInput, boolean excludeStorageBuses,
                                  IActionSource source, Predicate<AEKey> filter) {
        MEStorage network = grid.getStorageService().getInventory();
        MEStorage from = fillHost ? network : hostStorage;
        MEStorage destination = fillHost ? hostStorage : network;
        if (from != previousSource || destination != previousDestination) reset();
        previousSource = from;
        previousDestination = destination;
        boolean stableFilter = filter instanceof StorageInterfaceFilter && filter == previousFilter;
        KeyCounter available;
        boolean discoverAll = true;
        if (fillHost && !excludeStorageBuses && !StorageBusTransferFilter.isActive()) {
            available = grid.getStorageService().getCachedInventory();
        } else {
            discoverAll = refreshListing(from, stableFilter, filter);
            available = outputListing;
        }
        if (discoverAll || !stableFilter) {
            int listed = 0;
            for (var entry : available) {
                if (entry.getLongValue() > 0) {
                    listed++;
                    if (filter.test(entry.getKey())) queue.add(entry.getKey());
                }
            }
            if (queue.size() > listed) queue.removeIf(key -> available.get(key) <= 0);
        }
        previousFilter = filter;

        long moved = 0;
        int remaining = Math.min(KEYS_PER_TICK, queue.size());
        while (remaining-- > 0 && !queue.isEmpty()) {
            AEKey key = queue.removeFirst();
            if (!filter.test(key)) continue;
            long amount = available.get(key);
            if (amount <= 0) continue;
            long possible = destination.insert(key, amount, Actionable.SIMULATE, source);
            if (possible <= 0) { queue.add(key); continue; }
            // Avoid extracting from a source that may reject a refund when there is no power.
            double factor = Math.max(1.0, key.getAmountPerOperation());
            double power = grid.getEnergyService().extractAEPower(possible / factor,
                Actionable.SIMULATE, appeng.api.config.PowerMultiplier.CONFIG);
            possible = Math.min(possible, (long) (power * factor + 0.9));
            if (possible <= 0) { queue.add(key); break; }
            long extracted = fillHost && ignoreCreativeInput
                ? ECOCreativeExtractionFilter.extract(from, key, possible, source)
                : from.extract(key, possible, Actionable.MODULATE, source);
            if (extracted <= 0) { queue.add(key); continue; }
            long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), destination, key, extracted, source);
            if (inserted < extracted) from.insert(key, extracted - inserted, Actionable.MODULATE, source);
            if (inserted > 0) moved = inserted > Long.MAX_VALUE - moved ? Long.MAX_VALUE : moved + inserted;
            // Consumed and replenished stock can produce a zero net delta; keep the candidate until a read says empty.
            queue.add(key);
        }
        return moved;
    }

    private boolean refreshListing(MEStorage source, boolean discoverChanges, Predicate<AEKey> filter) {
        boolean scoped = StorageBusTransferFilter.isActive();
        boolean exact = ExactAmountCollector.isActive();
        long version = StorageListingVersion.versionOf(source);
        // Scope-specific listings never reuse an unfiltered native journal.
        boolean scopeIndependent = source instanceof cn.dancingsnow.neoecoae.impl.storage.StorageListingVersion;
        boolean reusable = (!scoped || scopeIndependent) && !exact
            && listedBusScope == scoped && listedVersion >= 0 && version >= 0;
        if (reusable) {
            if (version == listedVersion) return false;
            if (StorageListingVersion.drain(source, this, (key, amount) -> {
                outputListing.set(key, amount);
                if (amount <= 0) queue.remove(key);
                else if (discoverChanges && filter.test(key)) queue.add(key);
            })) {
                listedVersion = version;
                return false;
            }
        }
        outputListing.clear();
        source.getAvailableStacks(outputListing);
        outputListing.removeZeros();
        if ((!scoped || scopeIndependent) && !exact && version >= 0) StorageListingVersion.drain(source, this, (key, amount) -> {});
        listedVersion = scoped && !scopeIndependent || exact ? -1 : version;
        listedBusScope = scoped;
        return true;
    }
}
