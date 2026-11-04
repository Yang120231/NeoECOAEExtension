package cn.dancingsnow.neoecoae.api.me.bigorder;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.impl.storage.persistence.StorageAmounts;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** The inherited counter remains authoritative for its long window; GTO quantities own only its exact excess. */
public final class ECOExactInventory extends ListCraftingInventory {
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private final StorageAmounts excess = new StorageAmounts();
    private final ChangeListener listener;
    private boolean enabled;

    public ECOExactInventory(ChangeListener listener) {
        super(listener);
        this.listener = listener;
    }

    public boolean isEnabled() { return enabled; }
    public boolean isEmpty() { return list.isEmpty() && excess.isEmpty(); }

    public void setEnabled(boolean requested) {
        // AE2 may mutate the public counter directly. Keep that window live, including during cancellation.
        enabled = requested || !excess.isEmpty();
    }

    public BigInteger amount(AEKey key) {
        return BigInteger.valueOf(Math.max(0L, list.get(key))).add(excess.get(key).toBigInteger());
    }

    public long availableAfterReservations(AEKey key, long removed, long reserved) {
        if (!enabled) throw new IllegalStateException("Exact inventory is disabled");
        return excess.availableWithBase(key, Math.max(0L, list.get(key)), removed, reserved);
    }

    public static BigInteger amount(ListCraftingInventory inventory, AEKey key) {
        return inventory instanceof ECOExactInventory exact && exact.isEnabled()
            ? exact.amount(key) : BigInteger.valueOf(Math.max(0L, inventory.list.get(key)));
    }

    public Map<AEKey, BigInteger> snapshot() {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        list.fastForEach((key, amount) -> { if (amount > 0) result.put(key, BigInteger.valueOf(amount)); });
        excess.visitExact((key, amount) -> result.merge(key, amount, BigInteger::add));
        return result;
    }

    private void window(AEKey key, long amount) {
        if (amount == 0) list.remove(key); else list.set(key, amount);
    }

    private void set(AEKey key, BigInteger amount) {
        long visible = amount.min(MAX).longValueExact();
        if (amount.compareTo(MAX) > 0) excess.set(key, HugeAmount.of(amount.subtract(MAX)));
        else excess.setLong(key, 0);
        window(key, visible);
    }

    private void insertAmount(AEKey key, long amount) {
        long before = Math.max(0, list.get(key));
        long intoWindow = Math.min(amount, Long.MAX_VALUE - before);
        long visible = before + intoWindow;
        if (amount > intoWindow) excess.add(key, amount - intoWindow);
        // A foreign AE2 counter debit may have opened space in the window.
        if (visible < Long.MAX_VALUE && !excess.isEmpty()) visible += excess.extract(key, Long.MAX_VALUE - visible);
        if (visible != before) window(key, visible);
    }

    private long extractAmount(AEKey key, long amount) {
        long before = Math.max(0, list.get(key));
        long fromExcess = excess.isEmpty() ? 0 : excess.extract(key, amount);
        long fromWindow = Math.min(amount - fromExcess, before);
        long visible = before - fromWindow;
        if (visible < Long.MAX_VALUE && !excess.isEmpty()) visible += excess.extract(key, Long.MAX_VALUE - visible);
        if (visible != before || visible == 0) window(key, visible);
        return fromExcess + fromWindow;
    }

    private void credit(AEKey key, BigInteger amount) {
        if (amount.bitLength() < 64) insertAmount(key, amount.longValueExact());
        else set(key, amount(key).add(amount));
    }

    private void debitKey(AEKey key, BigInteger amount) {
        if (amount.bitLength() < 64) extractAmount(key, amount.longValueExact());
        else set(key, amount(key).subtract(amount));
    }

    @Override public void insert(AEKey key, long amount, Actionable mode) {
        if (!enabled) { super.insert(key, amount, mode); return; }
        if (amount < 0) throw new IllegalArgumentException("Negative inventory insertion");
        if (mode == Actionable.MODULATE && amount > 0) {
            insertAmount(key, amount);
            listener.onChange(key);
        }
    }

    @Override public long extract(AEKey key, long requested, Actionable mode) {
        if (!enabled) return super.extract(key, requested, mode);
        if (requested < 0) throw new IllegalArgumentException("Negative inventory extraction");
        if (requested == 0) return 0;
        if (mode == Actionable.SIMULATE) return Math.min(requested, availableAfterReservations(key, 0, 0));
        long extracted = extractAmount(key, requested);
        if (extracted > 0) listener.onChange(key);
        return extracted;
    }

    /** Validate all keys first, then change all keys before publishing notifications. */
    public boolean debit(Map<AEKey, BigInteger> inputs) {
        if (!enabled) throw new IllegalStateException("Exact inventory is disabled");
        for (var entry : inputs.entrySet()) {
            BigInteger requested = entry.getValue();
            if (requested.signum() <= 0) throw new IllegalArgumentException("Invalid exact input");
            if (requested.bitLength() < 64) {
                if (availableAfterReservations(entry.getKey(), 0, 0) < requested.longValueExact()) return false;
            } else if (amount(entry.getKey()).compareTo(requested) < 0) return false;
        }
        inputs.forEach(this::debitKey);
        try {
            inputs.keySet().forEach(listener::onChange);
        } catch (RuntimeException failure) {
            inputs.forEach(this::credit);
            throw failure;
        }
        return true;
    }

    public void restore(Map<AEKey, BigInteger> inputs) {
        if (!enabled) throw new IllegalStateException("Exact inventory is disabled");
        for (var amount : inputs.values()) if (amount.signum() <= 0) throw new IllegalArgumentException("Invalid exact input");
        inputs.forEach(this::credit);
        inputs.keySet().forEach(listener::onChange);
    }

    public static Map<AEKey, BigInteger> totals(List<GenericStack> stacks, long copies) {
        return totals(stacks, BigInteger.valueOf(copies));
    }

    public static Map<AEKey, BigInteger> totals(List<GenericStack> stacks, BigInteger copies) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        if (copies.signum() <= 0) throw new IllegalArgumentException("Invalid copies");
        for (var stack : stacks) {
            if (stack.amount() <= 0) throw new IllegalArgumentException("Invalid stack");
            result.merge(stack.what(), BigInteger.valueOf(stack.amount()).multiply(copies), BigInteger::add);
        }
        return result;
    }

    @Override public void clear() {
        excess.clear();
        super.clear();
    }

    @Override public ListTag writeToNBT() {
        if (!enabled) return super.writeToNBT();
        var result = new ListTag();
        snapshot().forEach((key, amount) -> {
            var tag = key.toTagGeneric();
            tag.putLong("#", amount.min(MAX).longValueExact());
            if (amount.compareTo(MAX) > 0) tag.putString("ecoExactAmount", amount.toString());
            result.add(tag);
        });
        return result;
    }

    @Override public void readFromNBT(ListTag data) {
        if (!enabled) {
            boolean exactData = false;
            for (int i = 0; i < data.size(); i++) exactData |= data.getCompound(i).contains("ecoExactAmount", Tag.TAG_STRING);
            if (!exactData) { excess.clear(); super.readFromNBT(data); return; }
        }
        // Decode first. Invalid exact data must never become a truncated long window.
        var restored = new LinkedHashMap<AEKey, BigInteger>();
        boolean hasExact = false;
        for (int i = 0; i < data.size(); i++) {
            var tag = data.getCompound(i);
            var key = AEKey.fromTagGeneric(tag);
            boolean exact = tag.contains("ecoExactAmount", Tag.TAG_STRING);
            if (key == null) {
                if (exact) throw new IllegalArgumentException("Unknown key in exact inventory");
                continue;
            }
            var count = exact ? new BigInteger(tag.getString("ecoExactAmount")) : BigInteger.valueOf(tag.getLong("#"));
            if (count.signum() < 0) throw new IllegalArgumentException("Negative saved inventory");
            restored.merge(key, count, BigInteger::add);
            hasExact |= exact;
        }
        list.clear();
        excess.clear();
        enabled |= hasExact;
        restored.forEach(this::set);
        enabled |= !excess.isEmpty();
        restored.keySet().forEach(listener::onChange);
    }
}
