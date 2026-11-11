package cn.dancingsnow.neoecoae.impl.storage.persistence;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyBigMap;
import appeng.api.stacks.AEKeyMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import cn.dancingsnow.neoecoae.compat.gto.GtoAmounts;
import cn.dancingsnow.neoecoae.impl.storage.StorageChangeLog;
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
 * GTO's quantity tables, shared by finite cells, infinite domains and exact-order inventory.
 * Ordinary cells use AEKeyMap, promoting to AEKeyBigMap above long. Infinite domains start wide.
 * ECO owns persistence and the optional change journal; no second cell-cache quantity table is maintained.
 * Above the 126-bit native window, ECO's U128/segmented quantities retain the exact balance.
 */
public class StorageAmounts {
    private AEKeyMap<AEKey> longAmounts;
    private AEKeyBigMap<AEKey> amounts;
    private final Reference2LongMap<AEKey> view = Reference2LongMaps.unmodifiable(new VisibleAmounts());
    private final Reference2ObjectOpenHashMap<AEKey, OverflowAmount> overflow = new Reference2ObjectOpenHashMap<>();
    private final StorageTotal total = new StorageTotal();
    private StorageChangeLog listingChanges;
    private static final HugeAmount NATIVE_MAX = HugeAmount.of(GtoAmounts.MAX);
    private long contentRevision;
    private long structureRevision;

    public StorageAmounts() { this(false); }

    protected StorageAmounts(boolean wide) {
        if (wide) amounts = new AEKeyBigMap<>();
        else longAmounts = new AEKeyMap<>();
    }

    private void promote() {
        if (amounts != null) return;
        amounts = new AEKeyBigMap<>();
        amounts.putAll(longAmounts);
        longAmounts = null;
    }

    /** All aliases share this owner, whose identity survives quantity-table promotion. */
    public Object resourceIdentity() { return this; }
    public long visible(AEKey key) { return longAmounts != null ? longAmounts.getAmount(key) : amounts.getLongAmount(key); }
    public Reference2LongMap<AEKey> visibleAmounts() { return view; }
    public int size() { return longAmounts != null ? longAmounts.size() : amounts.size(); }
    public long totalVisible() { return total.visible(); }
    public HugeAmount totalAmount() { return total.snapshot(); }
    public BigInteger totalExact() { return total.snapshot().toBigInteger(); }
    public boolean hasWideAmounts() {
        if (longAmounts != null) return false;
        for (var entry : amounts) if (entry.getValue().bitLength() > 63) return true;
        return false;
    }
    public boolean isBig(AEKey key) {
        if (longAmounts != null) return false;
        BigInteger value = amounts.getAmount(key);
        return value != null && value.bitLength() > 63;
    }
    public long contentRevision() { return contentRevision; }
    public long structureRevision() { return structureRevision; }

    public boolean sameContents(StorageAmounts other) {
        if (size() != other.size()) return false;
        if (longAmounts != null) {
            for (var entry : longAmounts) {
                if (!HugeAmount.of(entry.getLongValue()).equals(other.get(entry.getKey()))) return false;
            }
            return true;
        }
        for (var entry : amounts) {
            if (!get(entry.getKey()).equals(other.get(entry.getKey()))) return false;
        }
        return true;
    }

    public HugeAmount get(AEKey key) {
        if (longAmounts != null) return HugeAmount.of(longAmounts.getAmount(key));
        OverflowAmount big = overflow.isEmpty() ? null : overflow.get(key);
        if (big != null) return big.snapshot();
        BigInteger value = amounts.getAmount(key);
        return value == null ? HugeAmount.ZERO : HugeAmount.of(value);
    }

    public long available(AEKey key, long removed, long reserved) {
        return availableWithBase(key, 0, removed, reserved);
    }

    public long availableWithBase(AEKey key, long base, long removed, long reserved) {
        if (reserved < 0) throw new IllegalArgumentException("Negative protected amount");
        if (longAmounts != null) return GtoAmounts.available(longAmounts.getAmount(key), base, removed, reserved);
        // A >126-bit balance cannot fall below Long.MAX_VALUE after two long-sized reservations.
        if (!overflow.isEmpty() && overflow.containsKey(key)) return Long.MAX_VALUE;
        return GtoAmounts.available(amounts.getAmount(key), base, removed, reserved);
    }

    public void setLong(AEKey key, long value) {
        if (key == null || value < 0) throw new IllegalArgumentException("Invalid storage key or amount");
        HugeAmount before = get(key);
        if (!before.isBig() && before.toLongSaturated() == value) return;
        int size = size();
        if (longAmounts != null) {
            if (value == 0) longAmounts.removeLong(key); else longAmounts.set(key, value);
        } else {
            if (value == 0) amounts.remove(key); else amounts.set(key, BigInteger.valueOf(value));
        }
        overflow.remove(key);
        total.subtract(before);
        total.add(value);
        mutated(key, size);
    }

    public void set(AEKey key, HugeAmount value) {
        if (key == null || value == null) throw new IllegalArgumentException("Missing storage key or amount");
        if (!value.isBig()) { setLong(key, value.toLongSaturated()); return; }
        HugeAmount before = get(key);
        if (before.equals(value)) return;
        int size = size();
        promote();
        BigInteger exact = value.toBigInteger();
        if (exact.bitLength() > 126) {
            amounts.put(key, GtoAmounts.MAX);
            overflow.put(key, new OverflowAmount(value));
        } else {
            amounts.put(key, exact);
            overflow.remove(key);
        }
        total.subtract(before);
        total.add(value);
        mutated(key, size);
    }

    public void add(AEKey key, long amount) {
        if (key == null || amount < 0) throw new IllegalArgumentException("Invalid storage insertion");
        if (amount == 0) return;
        int size = size();
        long remaining = amount;
        if (longAmounts != null) {
            long accepted = longAmounts.insert(key, amount, Long.MAX_VALUE);
            if (accepted == amount) {
                total.add(amount);
                mutated(key, size);
                return;
            }
            promote();
            remaining -= accepted;
        }
        OverflowAmount big = overflow.isEmpty() ? null : overflow.get(key);
        if (big != null) {
            big.add(remaining);
        } else {
            long nativeAdded = amounts.insert(key, BigInteger.valueOf(remaining), GtoAmounts.MAX).longValueExact();
            if (nativeAdded < remaining) {
                overflow.put(key, new OverflowAmount(NATIVE_MAX
                    .add(remaining - nativeAdded)));
            }
        }
        total.add(amount);
        mutated(key, size);
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
        int size = size();
        if (longAmounts != null) {
            long extracted = longAmounts.extract(key, requested);
            if (extracted > 0) {
                total.subtract(extracted);
                mutated(key, size);
            }
            return extracted;
        }
        OverflowAmount big = overflow.isEmpty() ? null : overflow.get(key);
        if (big != null) {
            HugeAmount remaining = big.subtract(requested);
            if (remaining != null) {
                amounts.put(key, remaining.toBigInteger());
                overflow.remove(key);
                total.subtract(requested);
                mutated(key, size);
            } else {
                total.subtract(requested);
                mutated(key, size);
            }
            return requested;
        }
        long extracted = amounts.extractLong(key, requested);
        if (extracted > 0) {
            total.subtract(extracted);
            mutated(key, size);
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
        if (isEmpty()) return;
        if (longAmounts != null) { longAmounts.clear(); longAmounts.trim(); }
        else { amounts.clear(); amounts.trim(); }
        overflow.clear(); overflow.trim(); total.clear();
        contentRevision++; structureRevision++;
        invalidateListing();
    }

    private void mutated(AEKey key, int beforeSize) {
        contentRevision++;
        if (beforeSize != size()) structureRevision++;
        if (listingChanges != null) listingChanges.record(key);
    }

    /** Allocate the bounded journal only after an incremental reader subscribes. */
    public StorageChangeLog.Cursor listingCursor() {
        if (listingChanges == null) listingChanges = new StorageChangeLog();
        return listingChanges.cursor();
    }

    public boolean visitListingChanges(StorageChangeLog.Cursor from, java.util.function.ObjLongConsumer<AEKey> visitor) {
        return listingChanges != null && listingChanges.visitSince(from, key -> visitor.accept(key, visible(key)));
    }

    public void invalidateListing() { if (listingChanges != null) listingChanges.invalidate(); }

    public boolean isEmpty() { return longAmounts != null ? longAmounts.isEmpty() : amounts.isEmpty(); }
    public boolean hasOverflow() { return !overflow.isEmpty(); }
    public void forEach(BiConsumer<AEKey, HugeAmount> action) {
        if (longAmounts != null) {
            longAmounts.fastForEach((key, value) -> action.accept(key, HugeAmount.of(value)));
            return;
        }
        amounts.forEach((key, value) -> {
            OverflowAmount big = overflow.isEmpty() ? null : overflow.get(key);
            action.accept(key, big != null ? big.snapshot() : HugeAmount.of(value));
        });
    }
    public void visitExact(BiConsumer<AEKey, BigInteger> action) {
        if (longAmounts != null) {
            longAmounts.fastForEach((key, value) -> action.accept(key, BigInteger.valueOf(value)));
            return;
        }
        amounts.forEach((key, value) -> {
            OverflowAmount big = overflow.isEmpty() ? null : overflow.get(key);
            action.accept(key, big == null ? value : big.snapshot().toBigInteger());
        });
    }
    public void visitVisible(java.util.function.ObjLongConsumer<AEKey> action) {
        if (longAmounts != null) longAmounts.fastForEach(action);
        else amounts.fastForEachLong(action);
    }

    private static final class OverflowAmount {
        private final StorageTotal value = new StorageTotal();
        private long untilNative;
        OverflowAmount(HugeAmount amount) { value.add(amount); threshold(amount); }
        private void threshold(HugeAmount amount) {
            untilNative = amount.compareTo(NATIVE_MAX) <= 0 ? 0
                : amount.subtract(NATIVE_MAX).toLongSaturated();
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
        @Override public boolean containsKey(Object key) {
            return longAmounts != null ? longAmounts.containsKey(key) : amounts.containsKey(key);
        }
        @Override public int size() { return StorageAmounts.this.size(); }
        @Override public ObjectSet<Reference2LongMap.Entry<AEKey>> reference2LongEntrySet() {
            return new AbstractObjectSet<>() {
                @Override public int size() { return StorageAmounts.this.size(); }
                @Override public ObjectIterator<Reference2LongMap.Entry<AEKey>> iterator() {
                    if (longAmounts != null) {
                        var entries = longAmounts.reference2LongEntrySet().fastIterator();
                        return new ObjectIterator<>() {
                            @Override public boolean hasNext() { return entries.hasNext(); }
                            @Override public Reference2LongMap.Entry<AEKey> next() {
                                var entry = entries.next();
                                return new BasicEntry<>(entry.getKey(), entry.getLongValue());
                            }
                        };
                    }
                    var entries = amounts.reference2ReferenceEntrySet().fastIterator();
                    return new ObjectIterator<>() {
                        @Override public boolean hasNext() { return entries.hasNext(); }
                        @Override public Reference2LongMap.Entry<AEKey> next() {
                            var entry = entries.next();
                            return new BasicEntry<>(entry.getKey(), AEKeyBigMap.saturateToLong(entry.getValue()));
                        }
                    };
                }
            };
        }
    }
}
