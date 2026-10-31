package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A parent consumes stock from a self-growing cycle; only an actual shortfall needs growth. */
class ECOGtoSeedRetentionTest {
    private final AEKey a = key("A"), b = key("B"), f = key("F"), x = key("X");
    // P1: A + F -> 2B, P2: B -> A, PX: A -> X. F is a boundary input without a producer.
    private final CompiledPattern p1 = fastPattern(0, b, 2, new GenericStack(a, 1), new GenericStack(f, 1));
    private final CompiledPattern p2 = fastPattern(1, a, 1, new GenericStack(b, 1));
    private final CompiledPattern px = fastPattern(2, x, 1, new GenericStack(a, 1));

    @Test void exactStockDoesNotGrowToReplenishTheSeed() throws Exception {
        var outcome = plan(inventory(100, 10_000), 100);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(Map.of(px.details(), 100L), outcome.state().patternTimes(), describe(outcome));
        assertEquals(100L, outcome.state().usedItems().get(a));
        assertEquals(0L, outcome.state().usedItems().get(f));
        assertFalse(has(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED), describe(outcome));
    }

    @Test void stockShortfallGrowsOnlyTheMissingAmount() throws Exception {
        var outcome = plan(inventory(99, 10_000), 100);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(Map.of(px.details(), 100L, p1.details(), 1L, p2.details(), 2L),
            outcome.state().patternTimes(), describe(outcome));
        assertEquals(99L, outcome.state().usedItems().get(a));
        assertEquals(1L, outcome.state().usedItems().get(f));
        assertFalse(has(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED), describe(outcome));
    }

    @Test void exactStockWithoutFuelStillUsesTheStoredSeeds() throws Exception {
        var outcome = plan(inventory(100, 0), 100);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(Map.of(px.details(), 100L), outcome.state().patternTimes());
        assertEquals(100L, outcome.state().usedItems().get(a));
        assertFalse(has(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED));
    }

    @Test void stockBeyondTheDemandNeedsNoGrowth() throws Exception {
        var outcome = plan(inventory(150, 10_000), 100);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(Map.of(px.details(), 100L), outcome.state().patternTimes());
        assertEquals(0L, outcome.state().usedItems().get(f));
    }

    private ComponentPlanner.Outcome plan(KeyCounter inventory, long amount) throws Exception {
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(x, List.of(px));
        producers.put(b, List.of(p1));
        producers.put(a, List.of(p2));
        producers.put(f, List.of());
        var network = new CompiledNetwork(x, producers, Set.of(), 3, 4);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        return new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, inventory, amount, true, ECOCancellation.NONE);
    }

    private KeyCounter inventory(long seeds, long fuel) {
        var inventory = new KeyCounter();
        if (seeds > 0) inventory.add(a, seeds);
        if (fuel > 0) inventory.add(f, fuel);
        return inventory;
    }

    private static boolean has(ComponentPlanner.Outcome outcome, PlannerDiagnostic.Code code) {
        return outcome.trace().diagnostics().stream().anyMatch(d -> d.code() == code);
    }

    private static String describe(ComponentPlanner.Outcome outcome) {
        return outcome.status() + " times=" + outcome.state().patternTimes() + " "
            + outcome.trace().diagnostics().stream().map(d -> d.code() + ": " + d.message()).toList();
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern fastPattern(int id, AEKey output, long amount, GenericStack... inputs) {
        var details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(output, amount));
        when(details.getOutputs()).thenReturn(outputs.toArray(GenericStack[]::new));
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, output, PlannerAmount.of(amount),
            Arrays.stream(inputs).map(i -> new CompiledInput(null, i.what(), i.amount(), true, null)).toList(),
            outputs, true, null, false, semantics);
    }
}
