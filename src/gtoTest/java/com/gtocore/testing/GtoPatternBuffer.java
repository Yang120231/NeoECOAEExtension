package com.gtocore.testing;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.compat.gto.ECOGtoProcessingDispatch;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import java.util.List;

/** Test-only GTO buffer layout. Real JAR contracts are checked separately with ASM. */
public class GtoPatternBuffer implements ICraftingProvider {
    private final BiMap<IPatternDetails, Slot> patterns = HashBiMap.create();
    public final Object circuitInventorySimulated = new Object();
    public final KeyCounter stored = new KeyCounter();
    public IPatternDetails pattern;
    public boolean reject;
    public boolean failAfterInsertion;
    public int pushes;

    public void bind(IPatternDetails pattern) {
        this.pattern = pattern;
        patterns.put(pattern, new Slot(stored));
    }
    @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
    @Override public boolean isBusy() { return false; }
    @Override public boolean pushPattern(IPatternDetails candidate, KeyCounter[] inputs) {
        if (candidate != pattern || reject) return false;
        pushes++;
        patterns.get(candidate).pushPattern(candidate, inputs);
        if (failAfterInsertion) throw new IllegalStateException("Native notification failed after insertion");
        return true;
    }

    public static final class Slot {
        private final KeyInventory<AEItemKey> items;
        private final KeyInventory<AEFluidKey> fluids;
        private final KeyCounter stored;
        public Slot(KeyCounter stored) {
            this.stored = stored;
            items = new KeyInventory<>(stored);
            fluids = new KeyInventory<>(stored);
        }
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            IPatternDetails.PatternInputSink sink = stored::add;
            if (!ECOGtoProcessingDispatch.emitBatch(pattern, inputs, sink))
                pattern.pushInputsToExternalInventory(inputs, sink);
            return true;
        }
    }
}
