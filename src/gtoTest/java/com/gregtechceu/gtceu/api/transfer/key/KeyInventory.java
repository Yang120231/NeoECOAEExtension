package com.gregtechceu.gtceu.api.transfer.key;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/** Test-only model of the GTO inventory's counted, simulated insertion boundary. */
public final class KeyInventory<K extends AEKey> {
    private final KeyCounter stored;
    public KeyInventory(KeyCounter stored) { this.stored = stored; }
    public long insert(K key, long amount, boolean simulate) {
        long accepted = Math.min(amount, Long.MAX_VALUE - stored.get(key));
        if (!simulate) stored.add(key, accepted);
        return accepted;
    }
}
