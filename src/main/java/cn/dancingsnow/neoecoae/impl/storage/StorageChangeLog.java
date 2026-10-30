package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.function.Consumer;

/**
 * Bounded, server-thread change log of keys whose listed amount may have changed. Readers keep their own
 * {@link Cursor}; a reader that fell behind the ring, or whose epoch differs after a structural change, must list
 * the whole inventory again. Consecutive writes of one key that no reader has consumed yet share one record.
 */
public final class StorageChangeLog {
    public static final int CAPACITY = 4096;
    private static final int MASK = CAPACITY - 1;

    /** Position of one reader; {@code epoch} identifies the structural generation it was taken in. */
    public record Cursor(long epoch, long sequence) {}

    private final AEKey[] ring = new AEKey[CAPACITY];
    private long sequence;
    private long epoch;
    // Highest sequence any reader has already consumed; a record below it must not be merged into.
    private long consumed;
    private ReferenceOpenHashSet<AEKey> scratch;

    public void record(AEKey key) {
        if (key == null) return;
        if (sequence > consumed && ring[(int) ((sequence - 1) & MASK)] == key) return;
        ring[(int) (sequence & MASK)] = key;
        sequence++;
    }

    /** Structural change: every reader lists again. */
    public void invalidate() {
        epoch++;
    }

    public Cursor cursor() {
        consumed = Math.max(consumed, sequence);
        return new Cursor(epoch, sequence);
    }

    /**
     * Visits each key changed since {@code from} once. Returns {@code false} without visiting anything when the
     * cursor is stale; the caller then lists everything and takes a new {@link #cursor()}.
     */
    public boolean visitSince(Cursor from, Consumer<AEKey> visitor) {
        if (from == null || from.epoch() != epoch || from.sequence() > sequence || sequence - from.sequence() > CAPACITY) {
            return false;
        }
        long count = sequence - from.sequence();
        if (count == 1) {
            visitor.accept(ring[(int) (from.sequence() & MASK)]);
        } else if (count > 1) {
            if (scratch == null) scratch = new ReferenceOpenHashSet<>();
            try {
                for (long i = from.sequence(); i < sequence; i++) {
                    AEKey key = ring[(int) (i & MASK)];
                    if (scratch.add(key)) visitor.accept(key);
                }
            } finally {
                scratch.clear();
            }
        }
        return true;
    }

    public long epoch() { return epoch; }
    public long sequence() { return sequence; }
}
