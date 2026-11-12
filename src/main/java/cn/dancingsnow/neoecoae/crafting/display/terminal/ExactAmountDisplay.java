package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.menu.me.common.GridInventoryEntry;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.format.ExactAmountFormatter;

/**
 * GTO draws terminal entries inside its repo-slot interface, where Mixin 0.8.5 cannot inject.
 * The screen marks the entry being drawn; AE2's own amount formatter then substitutes the exact value.
 */
public final class ExactAmountDisplay {
    private static Thread owner;
    private static AEKey key;
    private static long amount;
    private static ExactAmount exact;

    private ExactAmountDisplay() {}

    /** Returns true when an exact amount is active and {@link #end()} must be called. */
    public static boolean begin(int containerId, GridInventoryEntry entry) {
        if (entry == null || entry.getWhat() == null) return false;
        ExactAmount value = ExactAmountClientCache.get(containerId, entry.getWhat());
        if (value == null) return false;
        owner = Thread.currentThread();
        key = entry.getWhat();
        amount = entry.getStoredAmount();
        exact = value;
        return true;
    }

    public static void end() {
        exact = null;
        key = null;
        owner = null;
    }

    public static String format(AEKey what, long value, AmountFormat format, String original) {
        ExactAmount current = exact;
        if (current == null || value != amount || owner != Thread.currentThread() || !what.equals(key))
            return original;
        return format == AmountFormat.FULL ? ExactAmountFormatter.full(what, current)
            : ExactAmountFormatter.slot(what, current);
    }
}
