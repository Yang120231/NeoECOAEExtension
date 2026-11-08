package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.StorageCell;
import appeng.items.storage.CreativeCellItem;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.crafting.planner.solve.PlannerInventorySnapshot;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import cn.dancingsnow.neoecoae.grid.NEMachineLookup;
import cn.dancingsnow.neoecoae.impl.storage.ECOCreativeCell;

import java.util.HashSet;
import java.util.LinkedHashMap;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import java.util.Set;

/** Captured on the server thread, preserving unlimited-source identity alongside AE2's long counter. */
public final class ECOPlannerInventory {
    private ECOPlannerInventory() {}

    public static PlannerInventorySnapshot capture(IGrid grid) {
        var storage = grid.getStorageService().getInventory();
        var amounts = new LinkedHashMap<AEKey, PlannerAmount>();
        Set<AEKey> unbounded = collectUnboundedKeys(grid);
        ExactAmountCollector.begin();
        try {
            var inventory = storage.getAvailableStacks();
            for (var entry : inventory) if (entry.getLongValue() > 0) amounts.put(entry.getKey(),
                PlannerAmount.of(entry.getLongValue()));
            ExactAmountCollector.finish().forEach((key, amount) -> {
                if (amount.infinite()) unbounded.add(key);
                else amounts.put(key, amount.quantity());
            });
        } catch (RuntimeException | Error failure) {
            ExactAmountCollector.abort();
            throw failure;
        }
        return PlannerInventorySnapshot.exact(amounts, unbounded);
    }

    /**
     * Returns keys supplied by an unbounded storage source in this grid.  The ordinary AE2
     * aggregate only exposes a saturated long amount, so callers that move physical stock must
     * preserve this side-channel and never persist those generated resources.
     */
    public static Set<AEKey> collectUnboundedKeys(IGrid grid) {
        Set<AEKey> unbounded = new HashSet<>();
        for (var drive : grid.getMachines(ECODriveBlockEntity.class)) {
            if (!drive.isMounted() || !drive.isOnline()) continue;
            var cell = drive.getCellInventory();
            if (cell instanceof ECOCreativeCell creative) unbounded.addAll(creative.configuredKeys());
            if (cell instanceof ExactAmountSource source) markUnbounded(source, unbounded);
        }
        for (IChestOrDrive host : NEMachineLookup.getMachines(grid, IChestOrDrive.class)) {
            if (!host.isPowered()) continue;
            for (int slot = 0; slot < host.getCellCount(); slot++) {
                StorageCell cell = host.getOriginalCellInventory(slot);
                if (cell instanceof ExactAmountSource source) markUnbounded(source, unbounded);

                if (cell != null && host.getCellItem(slot) instanceof CreativeCellItem) {
                    // Native ME drives expose AE2 creative cells as StorageCell and report only
                    // Integer.MAX_VALUE per key. Keep their actual unlimited semantics in the
                    // planner snapshot instead of mistaking that display count for finite stock.
                    unbounded.addAll(cell.getAvailableStacks().keySet());
                }
            }
        }
        return unbounded;
    }

    private static void markUnbounded(ExactAmountSource source, Set<AEKey> target) {
        source.neoecoae$visitExactAmounts((key, amount) -> {
            if (amount.infinite()) target.add(key);
        });
    }
}
