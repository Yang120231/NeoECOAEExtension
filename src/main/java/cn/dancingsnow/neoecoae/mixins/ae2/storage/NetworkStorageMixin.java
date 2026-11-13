package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEKey;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.impl.storage.transfer.StorageBusTransferFilter;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.StorageListingAccumulator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import java.math.BigInteger;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;

/** Prevents storage totals from disappearing when an unbounded stack is combined with another inventory. */
@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageMixin implements ECOBigIntegerStorage {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    @Unique private final StorageListingAccumulator neoecoae$listing = new StorageListingAccumulator();

    @Shadow private boolean mountsInUse;
    @Shadow @Final private it.unimi.dsi.fastutil.objects.ObjectArrayList<Object> priorityInventory;

    @Shadow private boolean isQueuedForRemoval(MEStorage inventory) { throw new AssertionError(); }
    @Shadow private void flushQueuedOperations() { throw new AssertionError(); }

    @Override
    public BigInteger insertBigInteger(AEKey what, BigInteger amount, Actionable mode, IActionSource source) {
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(source, "source");
        if (amount.signum() < 0) throw new IllegalArgumentException("Amount must not be negative");
        if (amount.signum() == 0) return BigInteger.ZERO;
        if (amount.compareTo(MAX_LONG) <= 0) {
            long offered = amount.longValueExact();
            return checked(amount, BigInteger.valueOf(((MEStorage) (Object) this)
                    .insert(what, offered, mode, source)));
        }
        if (mountsInUse) return BigInteger.ZERO;

        // Follow GTO AE2's single priority-ordered traversal, with its key-type and exclusion checks.
        // Each finite sink receives one long-sized offer; an exact sink receives the full remainder.
        BigInteger remaining = amount;
        mountsInUse = true;
        try {
            for (Object entry : priorityInventory) {
                if (remaining.signum() <= 0) break;
                MEStorage inventory = ((NetworkStorageMountAccessor) entry).neoecoae$storage();
                if (isQueuedForRemoval(inventory) || !inventory.supportsKeyType(what.getType())
                        || StorageBusTransferFilter.excludes(inventory)
                        || cn.dancingsnow.neoecoae.impl.storage.transfer.ECOCreativeExtractionFilter.excludesInsert(inventory))
                    continue;
                BigInteger inserted;
                if (inventory instanceof ECOBigIntegerStorage exact) {
                    inserted = insertExact(exact, what, remaining, mode, source);
                } else {
                    BigInteger offered = remaining.min(MAX_LONG);
                    inserted = checked(offered, BigInteger.valueOf(
                        inventory.insert(what, offered.longValueExact(), mode, source)));
                }
                remaining = remaining.subtract(inserted);
            }
        } finally {
            mountsInUse = false;
        }
        flushQueuedOperations();
        return amount.subtract(remaining);
    }

    private static BigInteger insertExact(ECOBigIntegerStorage inventory, AEKey what, BigInteger amount,
            Actionable mode, IActionSource source) {
        return checked(amount, inventory.insertBigInteger(what, amount, mode, source));
    }

    private static BigInteger checked(BigInteger offered, BigInteger inserted) {
        if (inserted == null || inserted.signum() < 0 || inserted.compareTo(offered) > 0) {
            throw new IllegalStateException("Invalid storage insertion result: " + inserted + " for " + offered);
        }
        return inserted;
    }

    @WrapOperation(
        method = "insert",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J")
    )
    private long neoecoae$excludeStorageBusOutput(MEStorage storage, AEKey key, long amount,
            Actionable mode, IActionSource source, Operation<Long> original) {
        if (StorageBusTransferFilter.excludes(storage)
                || cn.dancingsnow.neoecoae.impl.storage.transfer.ECOCreativeExtractionFilter.excludesInsert(storage)) {
            return 0L;
        }
        return original.call(storage, key, amount, mode, source);
    }

    @WrapOperation(
        method = {"extract", "extractTracked", "extractFrom"},
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J")
    )
    private long neoecoae$filterCreativeInput(MEStorage storage, appeng.api.stacks.AEKey key, long amount,
            appeng.api.config.Actionable mode, appeng.api.networking.security.IActionSource source,
            Operation<Long> original) {
        if (StorageBusTransferFilter.excludes(storage)
                || cn.dancingsnow.neoecoae.impl.storage.transfer.StorageExtractionExclusions.contains(storage)) {
            return 0L;
        }
        return cn.dancingsnow.neoecoae.impl.storage.transfer.ECOCreativeExtractionFilter.extractSource(
            storage, () -> original.call(storage, key, amount, mode, source));
    }

    @WrapOperation(
        method = "fill",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"
        )
    )
    private void neoecoae$getAvailableStacksSaturated(
        MEStorage storage, KeyCounter output, Operation<Void> original
    ) {
        if (StorageBusTransferFilter.excludes(storage)) return;
        // Reuse an isolated counter for sources without a saturating listing. This keeps
        // Long.MAX_VALUE + ordinary stock safe in either mount order, while trusted ECO
        // sources can append directly without copying their entire inventory.
        neoecoae$listing.append(storage, output, counter -> original.call(storage, counter));
    }

    @WrapOperation(method = {"lambda$getAvailableStacks$2", "lambda$getAvailableStacksTracked$3"},
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"))
    private static void neoecoae$listGtoMount(MEStorage storage, KeyCounter output, Operation<Void> original) {
        if (StorageBusTransferFilter.excludes(storage)) return;
        cn.dancingsnow.neoecoae.compat.gto.ECOGtoStorageListing.append(storage, output, original);
    }
}
