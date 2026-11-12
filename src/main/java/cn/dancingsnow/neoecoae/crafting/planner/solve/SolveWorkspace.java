package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.Map;
import java.util.LinkedHashMap;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;

public final class SolveWorkspace {
    private final PlannerInventorySnapshot inventory;
    private final Map<AEKey, Integer> candidateChoice;

    public SolveWorkspace(KeyCounter inventory, Map<AEKey, Integer> candidateChoice) {
        this(PlannerInventorySnapshot.of(inventory), candidateChoice);
    }

    public SolveWorkspace(PlannerInventorySnapshot inventory, Map<AEKey, Integer> candidateChoice) {
        this.inventory = inventory;
        this.candidateChoice = candidateChoice;
    }
    PlannerInventorySnapshot inventory() { return inventory; }
    Map<AEKey, Integer> candidateChoice() { return candidateChoice; }

    static Map<AEKey, PlannerAmount> outputAmounts(CompiledPattern pattern, PlannerAmount times) {
        Map<AEKey, PlannerAmount> produced = new LinkedHashMap<>();
        for (var output : pattern.outputs()) {
            PlannerAmount total = PlannerAmount.of(output.amount()).multiply(times);
            produced.put(output.what(), produced.getOrDefault(output.what(), PlannerAmount.ZERO).add(total));
        }
        return produced;
    }

}
