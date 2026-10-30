package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import net.minecraft.world.item.ItemStack;

/**
 * Bounded finite-to-infinite transfer. The sealed source stays intact until the destination is durable.
 */
public final class ECOInfiniteStorageTransfer {
    /** Sources sealed per tick; each seal flushes the source disk. */
    public static final int SEALS_PER_TICK = 5;
    /** Resources restored back to finite disks per tick; migration into the domain uses {@link Budget} instead. */
    public static final int KEYS_PER_TICK = 5;
    private final Map<UUID, Cursor> cursors = new HashMap<>();

    /**
     * Legacy per-slot receipt. {@code keyTag} is {@code key.toTagGeneric().toString()}, computed once per resource
     * and shared with the sealed receipt.
     */
    @FunctionalInterface
    public interface LegacyReceipt {
        UUID apply(AEKey key, String keyTag, long amount);
    }

    public enum State { COMPLETE, IN_PROGRESS, BLOCKED }

    /**
     * Outcome of one step. {@code domainWide} marks a blocking reason owned by the destination, which every other
     * source would hit as well.
     */
    public record Result(State state, String reason, boolean domainWide) {
        public static final Result COMPLETE = new Result(State.COMPLETE, null, false);
        public static final Result IN_PROGRESS = new Result(State.IN_PROGRESS, null, false);

        public static Result blocked(String reason, boolean domainWide) {
            return new Result(State.BLOCKED, reason == null || reason.isEmpty() ? "Migration blocked" : reason, domainWide);
        }

        public boolean complete() { return state == State.COMPLETE; }
        public boolean blocked() { return state == State.BLOCKED; }
    }

    /**
     * Per-tick work allowance shared by every source migrated in that tick. The first resource of a tick is always
     * allowed so a migration advances even when the time budget is smaller than one step.
     */
    public static final class Budget {
        private final LongSupplier clock;
        private final long deadline;
        private final int maxKeys;
        private int keys;

        public Budget(LongSupplier clock, long nanos, int maxKeys) {
            this.clock = clock;
            this.deadline = clock.getAsLong() + Math.max(0L, nanos);
            this.maxKeys = Math.max(1, maxKeys);
        }

        /** Unlimited time, bounded resource count; for callers without a clock. */
        public static Budget keys(int maxKeys) {
            return new Budget(() -> 0L, Long.MAX_VALUE / 2, maxKeys);
        }

        public boolean progressed() { return keys > 0; }
        public int keys() { return keys; }

        /** True once this tick's allowance is used; never before the first resource. */
        public boolean exhausted() {
            return keys > 0 && (keys >= maxKeys || clock.getAsLong() - deadline >= 0);
        }

        void consume(int count) {
            keys = (int) Math.min(Integer.MAX_VALUE, (long) keys + Math.max(1, count));
        }
    }

    public static boolean isEligible(IECOStorageCell cell) {
        return cell instanceof IECOStorageMigrationCell && cell.isInfiniteStorageEligible()
                && cell.getTier() == ECOTier.L9;
    }

    /** Receipt identity of one sealed resource: {@code nameUUID(migration + ":" + keyTag)}. */
    public static UUID sealedReceipt(UUID migration, String keyTag) {
        return UUID.nameUUIDFromBytes((migration + ":" + keyTag).getBytes(StandardCharsets.UTF_8));
    }

    /** Fraction of the current source already handled, in [0, 1]; 0 when no cursor is open. */
    public double progress(UUID migration) {
        Cursor cursor = cursors.get(migration);
        if (cursor == null || cursor.total <= 0) return 0.0D;
        return Math.min(1.0D, cursor.visited / (double) cursor.total);
    }

    /** Diagnostic/compatibility form: one step with a {@link #KEYS_PER_TICK} allowance; {@code true} once complete. */
    public boolean step(ItemStack source, IECOStorageMigrationCell cell, UUID domain,
                        ECOInfiniteStorageEngine engine, Runnable persistSeal, Runnable complete,
                        java.util.function.BiFunction<AEKey, Long, UUID> legacyReceipt) {
        return step(source, cell, domain, engine, persistSeal, complete,
            (key, keyTag, amount) -> legacyReceipt.apply(key, amount), Map.of(), Budget.keys(KEYS_PER_TICK)).complete();
    }

    /**
     * persistSeal must save the source contents and its seal before this method exposes any destination contents.
     * complete clears the source and marks it as a member, using the same live cell handler.
     */
    public Result step(ItemStack source, IECOStorageMigrationCell cell, UUID domain,
                       ECOInfiniteStorageEngine engine, Runnable persistSeal, Runnable complete, LegacyReceipt legacyReceipt,
                       Map<AEKey, java.util.Set<UUID>> releaseCompatibility, Budget budget) {
        if (!isEligible(cell)) return Result.blocked("Source disk is no longer eligible for migration", false);
        if (!engine.isHealthy()) return Result.blocked(engine.migrationBlockReason(), true);
        UUID migration = ECOInfiniteStorageMember.beginMigration(source, domain);
        if (cell instanceof cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell ordinary
            && engine instanceof SavedDataInfiniteStorageEngine saved && ordinary.storageSession() != null) {
            if (!ordinary.storageWritable()) {
                return Result.blocked("Source disk is not writable: " + ordinary.storageData().failure(), false);
            }
            ordinary.storageData().metadata(cn.dancingsnow.neoecoae.impl.storage.persistence.ECOCellReference.metadata(source));
            Map<AEKey, java.util.Set<UUID>> compatibility = new HashMap<>();
            if (ordinary.storageData().legacyMigration()) ordinary.storageData().amounts().visitVisible((key, amount) -> {
                String keyTag = key.toTagGeneric().toString();
                UUID legacy = legacyReceipt.apply(key, keyTag, amount);
                UUID sealed = sealedReceipt(migration, keyTag);
                java.util.Set<UUID> ids = new java.util.HashSet<>(); if (legacy != null) ids.add(legacy); ids.add(sealed);
                compatibility.put(key, java.util.Set.copyOf(ids));
            });
            releaseCompatibility.forEach((key, ids) -> {
                var combined = new java.util.HashSet<>(compatibility.getOrDefault(key, java.util.Set.of()));
                combined.addAll(ids); compatibility.put(key, java.util.Set.copyOf(combined));
            });
            // The journaled transfer is one atomic unit; it is charged as the number of resources it moves.
            budget.consume(ordinary.storageData().amounts().size());
            if (!cn.dancingsnow.neoecoae.impl.storage.persistence.StorageTransferCoordinator.transfer(
                ordinary.storageSession(), ordinary.storageData(), domain, saved, migration, compatibility)) {
                String domainReason = saved.migrationBlockReason();
                if (domainReason != null) return Result.blocked(domainReason, true);
                String sourceFailure = ordinary.storageData().failure();
                return Result.blocked(sourceFailure != null ? "Source disk transfer did not complete: " + sourceFailure
                    : "Transfer could not be saved; sealed source retained for retry", false);
            }
            cn.dancingsnow.neoecoae.impl.storage.persistence.ECOCellReference.applyMetadata(source, ordinary.storageData().metadata());
            complete.run();
            return Result.COMPLETE;
        }
        return step(migration, source, cell, engine, persistSeal, complete, legacyReceipt, budget);
    }

    Result step(UUID migration, Object source, IECOStorageMigrationCell cell,
                ECOInfiniteStorageEngine engine, Runnable persistSeal, Runnable complete, LegacyReceipt legacyReceipt,
                Budget budget) {
        if (!cell.isInfiniteStorageEligible()) return Result.blocked("Source disk is no longer eligible for migration", false);
        if (!engine.isHealthy()) return Result.blocked(engine.migrationBlockReason(), true);
        Cursor cursor = cursors.get(migration);
        if (cursor == null || cursor.source != source || cursor.cell != cell) {
            cell.persist();
            persistSeal.run();
            cursor = new Cursor(source, cell, cell.migrationEntries(), Math.max(0L, cell.getStoredItemTypes()));
            cursors.put(migration, cursor);
        }
        while (!budget.exhausted()) {
            if (cursor.key == null) {
                if (!cursor.entries.hasNext()) break;
                var entry = cursor.entries.next();
                cursor.key = entry.getKey();
                cursor.amount = entry instanceof it.unimi.dsi.fastutil.objects.Reference2LongMap.Entry<AEKey> reference
                    ? reference.getLongValue()
                    : ((it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey>) entry).getLongValue();
            }
            if (cursor.amount > 0) {
                // One serialization per resource, shared by the sealed and the legacy receipt.
                String keyTag = cursor.key.toTagGeneric().toString();
                UUID legacy = legacyReceipt.apply(cursor.key, keyTag, cursor.amount);
                UUID sealed = sealedReceipt(migration, keyTag);
                if (engine.insertOnce(java.util.Set.of(legacy, sealed), cursor.key, cursor.amount) != cursor.amount) {
                    String reason = engine.migrationBlockReason();
                    return reason != null ? Result.blocked(reason, true)
                        : Result.blocked("Infinite storage domain rejected " + cursor.key + " x" + cursor.amount, false);
                }
            }
            cursor.key = null;
            cursor.visited++;
            budget.consume(1);
        }
        if (cursor.key != null || cursor.entries.hasNext()) return Result.IN_PROGRESS;
        var accepted = engine.acceptChanges();
        if (!accepted.successful()) {
            String reason = accepted.failure() != null ? accepted.failure() : engine.migrationBlockReason();
            return Result.blocked(reason != null ? reason : "Infinite storage domain refused the migration commit", true);
        }
        if (!engine.flush()) {
            String reason = engine.migrationBlockReason();
            return Result.blocked(reason != null ? reason : "Infinite storage domain could not be saved; retrying", true);
        }
        complete.run();
        cursors.remove(migration);
        return Result.COMPLETE;
    }

    public void reset() {
        cursors.clear();
    }

    /**
     * Reconciles a target against a durable plan, including a write whose chunk receipt was not saved yet.
     */
    public static boolean restoreTarget(IECOStorageMigrationCell cell, AEKey key,
                                        ECOInfiniteStorageEngine.RestoreTargetAmounts goal, IActionSource source) {
        java.math.BigInteger current = cell.getMigrationAmountBigInteger(key);
        if (current.compareTo(goal.beforeExact()) < 0 || current.compareTo(goal.afterExact()) > 0) {
            throw new IllegalStateException("Restore target quantity differs from its saved plan");
        }
        java.math.BigInteger remaining = goal.afterExact().subtract(current);
        if (remaining.signum() == 0) return true;
        java.math.BigInteger inserted = cell.insertForMigrationBigInteger(key, remaining, Actionable.MODULATE, source);
        return inserted.equals(remaining) && cell.getMigrationAmountBigInteger(key).equals(goal.afterExact());
    }

    private static final class Cursor {
        private final Object source;
        private final IECOStorageMigrationCell cell;
        private final Iterator<? extends java.util.Map.Entry<AEKey, Long>> entries;
        private final long total;
        private long visited;
        private AEKey key;
        private long amount;

        private Cursor(Object source, IECOStorageMigrationCell cell, Iterator<? extends java.util.Map.Entry<AEKey, Long>> entries,
                       long total) {
            this.source = source;
            this.cell = cell;
            this.entries = entries;
            this.total = total;
        }
    }
}
