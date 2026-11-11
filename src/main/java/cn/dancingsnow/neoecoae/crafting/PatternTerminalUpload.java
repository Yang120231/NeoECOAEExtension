package cn.dancingsnow.neoecoae.crafting;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Shared settlement for encoded-pattern slots in AE2 and GTO terminals. */
public final class PatternTerminalUpload {
    private PatternTerminalUpload() {}

    public static void upload(IGrid grid, Player player, Slot slot) {
        var service = grid.getService(IECOPatternStorageService.class);
        if (service == null || !slot.hasItem()) return;
        ItemStack pattern = slot.getItem().copyWithCount(1);
        var details = PatternDetailsHelper.decodePattern(pattern, player.level());
        if (details == null) return;

        var insertion = service.insertPreparedPatternReporting(
                new ECOPreparedPattern(pattern, details, AEItemKey.of(pattern)));
        if (insertion.result() == ECOPatternInsertionResult.INSERTED) {
            if (!insertion.consumedSource() || returnReplacement(grid, player, insertion.blankReplacement())) {
                slot.remove(1);
            }
        } else if (insertion.result() == ECOPatternInsertionResult.ALREADY_PRESENT
                && returnReplacement(grid, player, service.blankPatternReplacementFor(pattern))) {
            slot.remove(1);
        }
    }

    private static boolean returnReplacement(IGrid grid, Player player, ItemStack replacement) {
        if (replacement.isEmpty()) return false;
        var inventory = grid.getStorageService().getInventory();
        var key = AEItemKey.of(replacement);
        if (key == null) return false;
        var source = IActionSource.ofPlayer(player);
        long accepted = inventory.insert(key, replacement.getCount(), Actionable.SIMULATE, source);
        if (accepted < replacement.getCount()) return false;
        return inventory.insert(key, replacement.getCount(), Actionable.MODULATE, source)
                >= replacement.getCount();
    }
}
