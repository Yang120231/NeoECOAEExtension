package com.gtocore.testing;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

/** GTO wildcard admission intentionally does not use its inherited template-pattern map. */
public final class GtoWildcardPatternBuffer extends GtoPatternBuffer {
    private final Object blacklistedItems = new Object();
    private final Slot[] internalInventory = { new Slot(stored) };
    @Override public void bind(IPatternDetails pattern) { this.pattern = pattern; }
    @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        if (reject) return false;
        pushes++;
        return internalInventory[0].pushPattern(pattern, inputs);
    }
}
