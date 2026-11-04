package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.Ae2CraftingCpuFastPathMixin;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.CraftingServiceMixin;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@org.junit.jupiter.api.extension.ExtendWith(cn.dancingsnow.neoecoae.util.GtoNativeKeyExtension.class)
class ECOExternalCpuDagInteropTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @AfterEach void clearRegistry() { ECOPlanningResultRegistry.clear(); }

    @Test void ordinaryDagCannotHideAnExactMaterialOverflow() throws Exception {
        var result = ordinaryDag(false);
        result.setExactMaterials(Map.of(AEItemKey.of(Items.STONE),
            PlannerAmount.of(BigInteger.ONE.shiftLeft(64))), Map.of(), Map.of());
        assertFalse(ECOExternalCpuSupport.supportsPlan(result.plan()));
    }

    @Test void phasedDagWithDifferentConcreteInputsStillRequiresEcoRuntime() throws Exception {
        var result = ordinaryDag(false);
        var execution = result.executionPlan();
        var task = JdkCompat.first(execution.tasks());
        var changed = new ECOExecutionPlan.TaskSpec(task.id(), task.identity(), task.pattern(),
            task.runtimeInfo(), task.totalCount(), task.phaseIndex(), task.kind(),
            List.of(new PlannedInputAllocation(0, List.of(new PlannedInputAllocation.Run(
                AEItemKey.of(Items.DIRT), 1L, task.totalCount())))));
        assertUnprovenDagRejected(result, new ECOExecutionPlan(execution.signature(), execution.mode(),
            List.of(changed), execution.phases(), execution.schedule()));
    }

    @Test void phasedDagCannotConcealACyclePhase() throws Exception {
        var result = ordinaryDag(false);
        var execution = result.executionPlan();
        var phase = JdkCompat.first(execution.phases());
        var changed = new ECOExecutionPlan.PhaseSpec(phase.index(), phase.componentId(),
            ECOExecutionSchedule.Type.CYCLE, phase.taskIds(), List.of(), phase.dependencies());
        assertUnprovenDagRejected(result, new ECOExecutionPlan(execution.signature(), execution.mode(),
            execution.tasks(), List.of(changed), execution.schedule()));
    }

    private static void assertUnprovenDagRejected(ECOPlanningResult result, ECOExecutionPlan execution) {
        var contract = new ECOExecutionContract(result.planningId(), execution.signature(),
            ExecutionMode.PHASED_DAG, execution, null);
        try (var registry = mockStatic(ECOPlanningResultRegistry.class, CALLS_REAL_METHODS)) {
            registry.when(() -> ECOPlanningResultRegistry.resolveContract(result.plan(), null)).thenReturn(contract);
            assertFalse(ECOExternalCpuSupport.supportsPlan(result.plan()));
        }
    }

    private static ECOPlanningResult ordinaryDag(boolean multipleSteps) throws Exception {
        var stone = AEItemKey.of(Items.STONE);
        var intermediate = AEItemKey.of(Items.COBBLESTONE);
        var goal = AEItemKey.of(Items.STONE_BRICKS);
        var first = pattern(Items.PAPER, stone, multipleSteps ? intermediate : goal);
        var patterns = multipleSteps ? List.of(first, pattern(Items.BOOK, intermediate, goal)) : List.of(first);
        var crafting = mock(ICraftingService.class);
        when(crafting.getCraftingFor(any())).thenAnswer(call -> patterns.stream()
            .filter(pattern -> pattern.getPrimaryOutput().what().equals(call.getArgument(0))).toList());
        var inventory = new KeyCounter();
        inventory.add(stone, 4L);
        var result = new ECOCraftingPlannerService().createSession(crafting, goal, inventory)
            .plan(4L, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertTrue(ECOPlanningResultRegistry.isECOOwnedPlan(result.plan()));
        return result;
    }

    private static IPatternDetails pattern(net.minecraft.world.item.Item definition, AEKey input, AEKey output) {
        var ingredient = mock(IPatternDetails.IInput.class);
        when(ingredient.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(input, 1L)});
        when(ingredient.getMultiplier()).thenReturn(1L);
        var pattern = mock(IPatternDetails.class);
        doReturn(AEItemKey.of(definition)).when(pattern).getDefinition();
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{ingredient});
        var produced = new GenericStack(output, 1L);
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{produced});
        when(pattern.getPrimaryOutput()).thenReturn(produced);
        return pattern;
    }


}
