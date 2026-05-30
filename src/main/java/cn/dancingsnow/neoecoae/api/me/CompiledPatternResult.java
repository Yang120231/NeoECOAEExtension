package cn.dancingsnow.neoecoae.api.me;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the pre-computed output of a crafting pattern assembly so that
 * workers can avoid re-running {@code fillCraftingGrid / assemble /
 * getRemainingItems} on every job.
 * <p>
 * Instances are immutable snapshots. Callers must {@link #copyOutput()}
 * and {@link #copyRemaining()} to obtain mutable copies for thread use.
 * </p>
 */
public final class CompiledPatternResult {

    private final ItemStack outputItem;
    private final List<ItemStack> remainingItems;

    public CompiledPatternResult(ItemStack outputItem, List<ItemStack> remainingItems) {
        // Defensive copy to ensure immutability.
        this.outputItem = outputItem.copy();
        List<ItemStack> copy = new ArrayList<>(remainingItems.size());
        for (ItemStack s : remainingItems) {
            if (!s.isEmpty()) {
                copy.add(s.copy());
            }
        }
        this.remainingItems = List.copyOf(copy);
    }

    /**
     * Returns a mutable copy of the output item.
     */
    public ItemStack copyOutput() {
        return outputItem.copy();
    }

    /**
     * Returns mutable copies of the remaining items.
     */
    public List<ItemStack> copyRemaining() {
        List<ItemStack> copy = new ArrayList<>(remainingItems.size());
        for (ItemStack s : remainingItems) {
            copy.add(s.copy());
        }
        return copy;
    }

    /** For debugging only — returns the unmodifiable view. */
    public ItemStack getOutputItem() {
        return outputItem;
    }

    /** For debugging only — returns the unmodifiable view. */
    public List<ItemStack> getRemainingItems() {
        return remainingItems;
    }
}
