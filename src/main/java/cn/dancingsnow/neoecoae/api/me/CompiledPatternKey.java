package cn.dancingsnow.neoecoae.api.me;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import java.util.Arrays;
import java.util.Objects;

/**
 * Cache key for compiled pattern results. Combines the pattern's identity
 * with the input table signature to avoid incorrect cache hits when the
 * same pattern is used with different substitution inputs, NBT variants,
 * or container items.
 * <p>
 * This key is used only for in-memory caching within a single worker BE.
 * It is NOT persisted to NBT and does NOT survive world reload.
 * </p>
 */
public final class CompiledPatternKey {

    private final int patternHash;
    private final int inputSignature;
    private final int hashCode;

    /**
     * @param pattern  the crafting pattern (used via identity hash, not content)
     * @param table    the input KeyCounter array describing what items are provided
     */
    public CompiledPatternKey(Object pattern, KeyCounter[] table) {
        // Pattern identity: use System.identityHashCode for speed;
        // the pattern object is unique per pattern slot in AE2.
        this.patternHash = System.identityHashCode(pattern);

        // Input signature: hash each slot's AEKey + amount.
        // AEKey.hashCode() includes type and NBT where applicable.
        int sig = 1;
        if (table != null) {
            for (KeyCounter counter : table) {
                if (counter == null) continue;
                for (AEKey key : counter.keySet()) {
                    if (key == null) continue;
                    long amount = counter.get(key);
                    sig = 31 * sig + key.hashCode();
                    sig = 31 * sig + Long.hashCode(amount);
                }
            }
        }
        this.inputSignature = sig;

        this.hashCode = Objects.hash(patternHash, inputSignature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompiledPatternKey that)) return false;
        return patternHash == that.patternHash && inputSignature == that.inputSignature;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "CompiledPatternKey{pattern=" + Integer.toHexString(patternHash)
                + ", inputSig=" + Integer.toHexString(inputSignature) + '}';
    }
}
