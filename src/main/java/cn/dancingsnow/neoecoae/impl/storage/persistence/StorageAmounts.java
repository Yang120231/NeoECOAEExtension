package cn.dancingsnow.neoecoae.impl.storage.persistence;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyBigMap;
import appeng.api.stacks.AEKeyObjectMap;
import appeng.api.stacks.U126;
import cn.dancingsnow.neoecoae.compat.gto.GtoAmounts;
import cn.dancingsnow.neoecoae.compat.gto.GtoBigCellStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractReference2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMaps;
import java.math.BigInteger;
import java.util.function.BiConsumer;

/**
 * GTO's quantity table, shared by finite cells, infinite domains and exact-order inventory.
 * Persistent records attach this very map to an owned BigCellDataStorage when their server session opens.
 * Only values beyond GTO's U126 range need an exact overflow record; ordinary IO is native, in-place arithmetic.
 */
public class StorageAmounts {
    private final AEKeyBigMap<AEKey> amounts = new AEKeyBigMap<>();
    private final Reference2LongMap<AEKey> view = Reference2LongMaps.unmodifiable(new VisibleAmounts());
    private final AEKeyObjectMap<AEKey, OverflowAmount> overflow = new AEKeyObjectMap<>();
    private final StorageTotal total = new StorageTotal();
    private GtoBigCellStorage nativeStorage;
    private long contentRevision;
    private long structureRevision;

    public void attachNativeStorage() {
        if (nativeStorage == null) nativeStorage = GtoBigCellStorage.create(amounts);
    }

    public boolean isNativeBacked() { return nativeStorage != null; }
    public Object resourceIdentity() { return nativeStorage == null ? this : nativeStorage.identity(); }
    public long visible(AEKey key) { return amounts.getLongAmount(key); }
    public Reference2LongMap<AEKey> visibleAmounts() { return view; }
    public int size() { return amounts.size(); }
    public long totalVisible() { return total.visible(); }
    public HugeAmount totalAmount() { return total.snapshot(); }
    public BigInteger totalExact() { return total.snapshot().toBigInteger(); }
    public boolean hasWideAmounts() {
        for (var entry : amounts) if (entry.getValue().isAboveLong()) return true;
        return false;
    }
    public boolean isBig(AEKey key) {
        U126 value = amounts.getAmount(key);
        return value != null && value.isAboveLong();
    }
    public long contentRevision() { return contentRevision; }
    public long structureRevision() { return structureRevision; }

    public boolean sameContents(StorageAmounts other) {
        if (size() != other.size()) return false;
        for (var entry : amounts) {
            if (!get(entry.getKey()).equals(other.get(entry.getKey()))) return false;
        }
        return true;
    }

    public HugeAmount get(AEKey key) {
        OverflowAmount big = overflow.isEmpty() ? null : overflow.get(key);
        if (big != null) return big.snapshot();
        U126 value = amounts.getAmount(key);
        return value == null ? HugeAmount.ZERO : value.isAboveLong()
            ? HugeAmount.of(cn.dancingsnow.neoecoae.crafting.amount.U126.fromGto(value)) : HugeAmount.of(value.lo());
    }

    public long available(AEKey key, long removed, long reserved) {
        return availableWithBase(key, 0, removed, reserved);
    }

    public long availableWithBase(AEKey key, long base, long removed, long reserved) {
        if (reserved < 0) throw new IllegalArgumentException("Negative protected amount");
        // A >126-bit balance cannot fall below Long.MAX_VALUE after two long-sized reservations.
        if (!overflow.isEmpty() && overflow.containsKey(key)) return Long.MAX_VALUE;
        return GtoAmounts.available(amounts.getAmount(key), base, removed, reserved);
    }

    public void setLong(AEKey key, long value) {
        if (key == null || value < 0) throw new IllegalArgumentException("Invalid storage key or amount");
        HugeAmount before = get(key);
        if (!before.isBig() && before.toLongSaturated() == value) return;
        int size = amounts.size();
        if (value == 0) amounts.remove(key); else amounts.set(key, value);
        overflow.remove(key);
        total.subtract(before);
        total.add(value);
        replaced(key, size);
    }

    public void set(AEKey key, HugeAmount value) {
        if (key == null || value == null) throw new IllegalArgumentException("Missing storage key or amount");
        if (!value.isBig()) { setLong(key, value.toLongSaturated()); return; }
        HugeAmount before = get(key);
        if (before.equals(value)) return;
        int size = amounts.size();
        BigInteger exact = value.toBigInteger();
        if (exact.bitLength() > 126) {
            U126 limit = new U126();
            limit.saturate();
            amounts.put(key, limit);
            overflow.put(key, new OverflowAmount(value));
        } else {
            amounts.put(key, GtoAmounts.fromExact(exact));
            overflow.remove(key);
        }
        total.subtract(before);
        total.add(value);
        replaced(key, size);
    }

    public void add(AEKey key, long amount) {
        if (key == null || amount < 0) throw new IllegalArgumentException("Invalid storage insertion");
        if (amount == 0) return;
        int size = amounts.size();
        OverflowAmount big = overflow.isEmpty() ? null : overflow.get(key);
        long nativeAdded;
        if (big != null) {
            big.add(amount);
            nativeAdded = 0;
        } else {
            nativeAdded = amounts.addTo(key, amount);
            if (nativeAdded < amount) {
                overflow.put(key, new OverflowAmount(HugeAmount.of(GtoAmounts.MAX)
                    .add(amount - nativeAdded)));
            }
        }
        total.add(amount);
        mutated(key, size, nativeAdded);
    }

    /** Compatibility for callers that already read a long window; the container owns the actual balance. */
    public void add(AEKey key, long current, long amount) { add(key, amount); }

    public void add(AEKey key, BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Invalid storage insertion");
        if (amount.bitLength() < 64) add(key, amount.longValueExact());
        else set(key, get(key).add(HugeAmount.of(amount)));
    }

    /** One native lookup on the usual path, including partial extraction and deleting an exhausted key. */
    public long extract(AEKey key, long requested) {
        if (key == null || requested < 0) throw new IllegalArgumentException("Invalid storage extraction");
        if (requested == 0) return 0;
        int size = amounts.size();
        OverflowAmount big = overflow.isEmpty() ? null : overflow.get(key);
        if (big != null) {
            HugeAmount remaining = big.subtract(requested);
            if (remaining != null) {
                amounts.put(key, GtoAmounts.fromExact(remaining.toBigInteger()));
                overflow.remove(key);
                total.subtract(requested);
                replaced(key, size);
            } else {
                total.subtract(requested);
                mutated(key, size, 0);
            }
            return requested;
        }
        long extracted = amounts.extractLong(key, requested);
        if (extracted > 0) {
            total.subtract(extracted);
            mutated(key, size, -extracted);
        }
        return extracted;
    }

    public void subtract(AEKey key, long amount) { subtract(key, visible(key), amount); }
    public long subtract(AEKey key, long current, long amount) {
        if (amount < 0 || amount > current || amount > visible(key)) throw new IllegalArgumentException("Invalid extraction");
        long extracted = extract(key, amount);
        if (extracted != amount) throw new IllegalStateException("Stale extraction balance");
        return visible(key);
    }

    public void clear() {
        if (amounts.isEmpty()) return;
        amounts.clear(); overflow.clear(); amounts.trim(); overflow.trim(); total.clear();
        if (nativeStorage != null) nativeStorage.refresh();
        contentRevision++; structureRevision++;
        cleared();
    }

    private void mutated(AEKey key, int beforeSize, long nativeDelta) {
        if (nativeStorage != null) nativeStorage.changed(key, nativeDelta);
        contentRevision++;
        if (beforeSize != amounts.size()) structureRevision++;
        changed(key);
    }

    private void replaced(AEKey key, int beforeSize) {
        if (nativeStorage != null) nativeStorage.refresh();
        contentRevision++;
        if (beforeSize != amounts.size()) structureRevision++;
        changed(key);
    }

    protected void changed(AEKey key) {}
    protected void cleared() {}
    public boolean isEmpty() { return amounts.isEmpty(); }
    public boolean hasOverflow() { return !overflow.isEmpty(); }
    public void forEach(BiConsumer<AEKey, HugeAmount> action) {
        amounts.forEach((key, value) -> {
            OverflowAmount big = overflow.isEmpty() ? null : overflow.get(key);
            action.accept(key, big != null ? big.snapshot() : value.isAboveLong()
                ? HugeAmount.of(cn.dancingsnow.neoecoae.crafting.amount.U126.fromGto(value)) : HugeAmount.of(value.lo()));
        });
    }
    public void visitExact(BiConsumer<AEKey, BigInteger> action) {
        amounts.forEach((key, value) -> {
            OverflowAmount big = overflow.isEmpty() ? null : overflow.get(key);
            action.accept(key, big == null ? GtoAmounts.exact(value) : big.snapshot().toBigInteger());
        });
    }
    public void visitVisible(java.util.function.ObjLongConsumer<AEKey> action) {
        if (nativeStorage != null) nativeStorage.listing().fastForEach(action);
        else amounts.fastForEachLong(action);
    }

    private static final class OverflowAmount {
        private final StorageTotal value = new StorageTotal();
        private long untilNative;
        OverflowAmount(HugeAmount amount) { value.add(amount); threshold(amount); }
        private void threshold(HugeAmount amount) {
            untilNative = amount.toU126() != null ? 0
                : amount.toBigInteger().subtract(GtoAmounts.MAX).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        }
        void add(long amount) {
            value.add(amount);
            untilNative = amount > Long.MAX_VALUE - untilNative ? Long.MAX_VALUE : untilNative + amount;
        }
        HugeAmount subtract(long amount) {
            value.subtract(amount);
            if (amount < untilNative) { untilNative -= amount; return null; }
            HugeAmount remaining = value.snapshot();
            threshold(remaining);
            return untilNative == 0 ? remaining : null;
        }
        HugeAmount snapshot() { return value.snapshot(); }
    }

    /** Live compatibility window, not a second quantity table. Hot iteration uses visitVisible instead. */
    private final class VisibleAmounts extends AbstractReference2LongMap<AEKey> {
        @Override public long getLong(Object key) { return key instanceof AEKey aeKey ? visible(aeKey) : 0; }
        @Override public boolean containsKey(Object key) { return amounts.containsKey(key); }
        @Override public int size() { return amounts.size(); }
        @Override public ObjectSet<Reference2LongMap.Entry<AEKey>> reference2LongEntrySet() {
            return new AbstractObjectSet<>() {
                @Override public int size() { return amounts.size(); }
                @Override public ObjectIterator<Reference2LongMap.Entry<AEKey>> iterator() {
                    var entries = amounts.reference2ObjectEntrySet().fastIterator();
                    return new ObjectIterator<>() {
                        @Override public boolean hasNext() { return entries.hasNext(); }
                        @Override public Reference2LongMap.Entry<AEKey> next() {
                            var entry = entries.next();
                            return new BasicEntry<>(entry.getKey(), entry.getValue().toLong());
                        }
                    };
                }
            };
        }
    }
}
