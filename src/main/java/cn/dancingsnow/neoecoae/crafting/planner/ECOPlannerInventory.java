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
import java.util.Set;

/** Captured on the server thread, preserving unlimited-source identity alongside AE2's long counter. */
public final class ECOPlannerInventory {
    private ECOPlannerInventory() {}

    private record CachedPlan(java.util.Map<AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> exact,
            PlannerInventorySnapshot snapshot) {}
    // Values do not retain their counter keys or world; old publications disappear after their consumers finish.
    private static final java.util.Map<appeng.api.stacks.KeyCounter, CachedPlan> PLANS = new java.util.WeakHashMap<>();

    public static void clearRuntimeState() { PLANS.clear(); }

    public static PlannerInventorySnapshot capture(IGrid grid) {
        var storage = cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot.resolve(
            grid.getStorageService().getInventory());
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        var snapshot = cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot.sample(
            storage, server == null ? 0L : server.getTickCount(), true, true, storage::getAvailableStacks);
        Set<AEKey> unbounded = collectUnboundedKeys(grid);
        snapshot.amounts().forEach((key, amount) -> { if (amount.infinite()) unbounded.add(key); });
        CachedPlan previous = PLANS.get(snapshot.inventory());
        if (previous != null && previous.exact() == snapshot.amounts()
                && previous.snapshot().unboundedKeys().equals(unbounded)) return previous.snapshot();
        var amounts = new LinkedHashMap<AEKey, PlannerAmount>();
        snapshot.visitVisibleAmounts((key, amount) -> {
            if (amount > 0) amounts.put(key, PlannerAmount.of(amount));
        });
        snapshot.amounts().forEach((key, amount) -> {
            if (!amount.infinite()) amounts.put(key, amount.quantity());
        });
        var captured = PlannerInventorySnapshot.exact(amounts, unbounded);
        PLANS.put(snapshot.inventory(), new CachedPlan(snapshot.amounts(), captured));
        return captured;
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
