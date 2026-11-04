package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOBigOrderPlanner;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlannerInventory;
import cn.dancingsnow.neoecoae.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.storage.ECOCreativeCell;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(cn.dancingsnow.neoecoae.util.GtoNativeKeyExtension.class)
class CreativeSupplyPlanningTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void onlyOnlineMountedEcoCreativeCellsConferUnlimitedSupply() {
        AEKey key = AEItemKey.of(Items.IRON_INGOT);
        KeyCounter stock = new KeyCounter();
        stock.set(key, Long.MAX_VALUE);
        IGrid grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(grid.getStorageService().getInventory().getAvailableStacks()).thenReturn(stock);
        when(grid.getMachines(ECODriveBlockEntity.class)).thenReturn(Set.of());
        assertFalse(counter(ECOPlannerInventory.capture(grid)).isUnbounded(key),
            "A Long.MAX_VALUE listing alone, including a vanilla drive, is never the capability");
        var drive = mock(ECODriveBlockEntity.class);
        var cell = mock(ECOCreativeCell.class);
        when(cell.configuredKeys()).thenReturn(Set.of(key));
        when(drive.getCellInventory()).thenReturn(cell);
        when(grid.getMachines(ECODriveBlockEntity.class)).thenReturn(Set.of(drive));
        when(drive.isMounted()).thenReturn(true);
        assertFalse(counter(ECOPlannerInventory.capture(grid)).isUnbounded(key));
        when(drive.isOnline()).thenReturn(true);
        var captured = ECOPlannerInventory.capture(grid);
        assertTrue(counter(captured).isUnbounded(key));
        assertEquals(Long.MAX_VALUE, captured.toKeyCounter().get(key));
        when(drive.isMounted()).thenReturn(false);
        assertFalse(counter(ECOPlannerInventory.capture(grid)).isUnbounded(key));
        when(drive.isMounted()).thenReturn(true);
        when(drive.getCellInventory()).thenReturn(null);
        assertFalse(counter(ECOPlannerInventory.capture(grid)).isUnbounded(key));
        assertTrue(counter(captured).isUnbounded(key), "An async calculation owns an immutable snapshot");
    }

    @Test void demandAboveLongMaxIsReservedExactlyAndStillRequiresSegmentation() throws Exception {
        AEKey key = AEItemKey.of(Items.IRON_INGOT);
        var infinite = solve(Long.MAX_VALUE, snapshot(key, true));
        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, infinite.status());
        assertTrue(infinite.state().missingAmounts().isEmpty());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO),
            infinite.state().usedAmounts().get(key).toBigInteger());
        assertNull(ECOPlanMaterialValidator.firstDeficit(infinite.state(), AEItemKey.of(Items.DIAMOND), Long.MAX_VALUE));
        assertFalse(infinite.state().executionAmountIssues().isEmpty());

        var finite = solve(Long.MAX_VALUE, snapshot(key, false));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), finite.state().missingAmounts().get(key).toBigInteger());
    }

    @Test void bigOrderSearchBuildsACompleteLongChildFromUnlimitedStock() throws Exception {
        var stock = snapshot(AEItemKey.of(Items.IRON_INGOT), true);
        var bridge = new AE2CraftingPlanBridge();
        var answer = ECOBigOrderPlanner.search(Long.MAX_VALUE, Long.MAX_VALUE, amount -> {
            var solved = solve(amount, stock);
            var goal = AEItemKey.of(Items.DIAMOND);
            var plan = solved.status() == PlanningStatus.SUCCESS
                ? bridge.success(goal, amount, false, false, solved.state()) : bridge.unsupported(goal, amount);
            return new ECOPlanningResult(solved.status(), plan, solved.trace(), List.of(), 0);
        });
        assertFalse(answer.fatal());
        assertEquals(PlanningStatus.SUCCESS, answer.result().status());
        var plan = answer.result().plan();
        assertTrue(plan.finalOutput().amount() < Long.MAX_VALUE);
        assertEquals(Math.multiplyExact(2, plan.finalOutput().amount()), plan.usedItems().get(AEItemKey.of(Items.IRON_INGOT)));
        assertTrue(plan.missingItems().isEmpty());
        // A new child/probe must not inherit the preceding child's reservations.
        assertEquals(PlanningStatus.SUCCESS, solve(plan.finalOutput().amount(), stock).status());
    }

    @Test void unboundedReservationsSurviveCopiesButGoalStockCanBeExcluded() {
        AEKey key = AEItemKey.of(Items.IRON_INGOT);
        var original = counter(snapshot(key, true));
        var huge = PlannerAmount.of(BigInteger.TEN.pow(30));
        var copy = original.copy();
        assertEquals(huge, copy.available(key, huge));
        copy.remove(key, huge);
        assertEquals(huge, copy.available(key, huge));
        var replaced = new PlannerCounter();
        replaced.replaceFrom(copy);
        assertTrue(replaced.isUnbounded(key));
        replaced.set(key, PlannerAmount.ZERO);
        assertEquals(PlannerAmount.ZERO, replaced.available(key, huge));
        assertTrue(original.isUnbounded(key));
    }

    @Test void cycleBoundaryCanReserveCreativeStockAfterEarlierReservations() throws Exception {
        AEKey key = AEItemKey.of(Items.IRON_INGOT);
        var inventory = snapshot(key, true);
        var base = new SolveState(inventory);
        base.used.add(key, Long.MAX_VALUE);
        var network = new CompiledNetwork(key, Map.of(key, List.of()), Set.of(), 0, 0);
        var cycle = mock(cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent.class);
        var outcome = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(network, cycle,
            Map.of(key, 64L), inventory.toKeyCounter(), base, Map.of(key, Long.MAX_VALUE), Set.of(key),
            false, ECOCancellation.NONE);
        assertTrue(outcome.solved());
        assertEquals(64, outcome.directReservations().get(key));
        assertTrue(outcome.delegatedCycleDemands().isEmpty(), "Creative supply does not need a cycle producer");
        assertTrue(outcome.missingLeaves().isEmpty());
    }

    private static ItemStack cellStack(Item item) {
        var stack = spy(new ItemStack(Items.PAPER));
        doReturn(item).when(stack).getItem();
        return stack;
    }

    private static IGrid cellGrid(StorageCell cell) {
        return cellGrid(cell, DriveBlockEntity.class);
    }

    private static <T extends IChestOrDrive> IGrid cellGrid(StorageCell cell, Class<T> hostType) {
        var host = mock(hostType);
        when(host.isPowered()).thenReturn(true);
        when(host.getCellCount()).thenReturn(1);
        when(host.getOriginalCellInventory(0)).thenReturn(cell);
        var network = new NetworkStorage();
        network.mount(0, cell);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(grid.getStorageService().getInventory()).thenReturn(network);
        when(grid.getMachineClasses()).thenReturn(Set.of(hostType));
        when(grid.getMachines(ECODriveBlockEntity.class)).thenReturn(Set.of());
        when(grid.getMachines(IChestOrDrive.class)).thenReturn(Set.of());
        when(grid.getMachines(hostType)).thenReturn(Set.of(host));
        return grid;
    }

    private static PlannerCounter counter(PlannerInventorySnapshot snapshot) {
        var result = new PlannerCounter();
        snapshot.initialize(result);
        return result;
    }

    private static PlannerInventorySnapshot snapshot(AEKey key, boolean unlimited) {
        KeyCounter stock = new KeyCounter();
        stock.set(key, Long.MAX_VALUE);
        return PlannerInventorySnapshot.of(stock, unlimited ? Set.of(key) : Set.of());
    }

    private static AcyclicCraftingSolver.Outcome solve(long amount, PlannerInventorySnapshot inventory)
            throws InterruptedException {
        return solve(amount, inventory, AEItemKey.of(Items.IRON_INGOT));
    }

    private static AcyclicCraftingSolver.Outcome solve(long amount, PlannerInventorySnapshot inventory, AEKey input)
            throws InterruptedException {
        AEKey output = AEItemKey.of(Items.DIAMOND);
        var details = mock(IPatternDetails.class);
        var ingredient = mock(IPatternDetails.IInput.class);
        when(ingredient.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(input, 2)});
        when(ingredient.getMultiplier()).thenReturn(1L);
        when(details.getInputs()).thenReturn(new IPatternDetails.IInput[]{ingredient});
        var outputs = List.of(new GenericStack(output, 1));
        when(details.getOutputs()).thenReturn(outputs.toArray(GenericStack[]::new));
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        var pattern = new CompiledPattern(0, details, output, PlannerAmount.of(1),
            List.of(new CompiledInput(ingredient, input, 2, false, null)), outputs, true, null, false, semantics);
        var network = new CompiledNetwork(output, Map.of(output, List.of(pattern), input, List.of()), Set.of(), 1, 1);
        return new AcyclicCraftingSolver().solve(network, new AcyclicRoutePlan(List.of(output, input)),
            inventory, amount, Map.of(), Set.of(), false, ECOCancellation.NONE);
    }
}
