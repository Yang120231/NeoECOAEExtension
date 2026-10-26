package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.StorageListingAccumulator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Reuses counters in GTO's static mount visitors while keeping recursive network listings independent. */
public final class ECOGtoStorageListing {
    private static final ThreadLocal<Pool> POOLS = ThreadLocal.withInitial(Pool::new);
    private ECOGtoStorageListing() {}

    public static void append(MEStorage storage, KeyCounter output, Operation<Void> original) {
        Pool pool = POOLS.get();
        if (pool.depth == pool.frames.size()) pool.frames.add(new Frame());
        Frame frame = pool.frames.get(pool.depth++);
        frame.storage = storage;
        frame.operation = original;
        try {
            frame.accumulator.append(storage, output, frame);
        } finally {
            frame.storage = null;
            frame.operation = null;
            pool.depth--;
        }
    }
    private static final class Pool {
        final ArrayList<Frame> frames = new ArrayList<>(2);
        int depth;
    }
    private static final class Frame implements Consumer<KeyCounter> {
        final StorageListingAccumulator accumulator = new StorageListingAccumulator();
        MEStorage storage;
        Operation<Void> operation;
        @Override public void accept(KeyCounter counter) { operation.call(storage, counter); }
    }
}
