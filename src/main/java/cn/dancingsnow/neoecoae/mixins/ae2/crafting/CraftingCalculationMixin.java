package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerRequest;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GTO replaces vanilla calculations. Explicit ECO requests retain the complete ECO planner and diagnostics. */
@Mixin(value = CraftingService.class, remap = false, priority = 500)
public abstract class CraftingCalculationMixin {
    @Shadow @Final private IGrid grid;

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "beginCraftingCalculation", at = @At("RETURN"))
    private Future<ICraftingPlan> neoecoae$markNativePlanning(Future<ICraftingPlan> future, Level level,
            ICraftingSimulationRequester requester, AEKey key, long amount, CalculationStrategy strategy) {
        return requester instanceof ECOPlannerRequest ? future
            : new cn.dancingsnow.neoecoae.compat.gto.ECOGtoPlanningFuture(future);
    }

    @Inject(method = "beginCraftingCalculation", at = @At("HEAD"), cancellable = true)
    private void neoecoae$plan(Level level, ICraftingSimulationRequester requester, AEKey key, long amount,
            CalculationStrategy strategy, CallbackInfoReturnable<Future<ICraftingPlan>> result) {
        if (requester instanceof ECOPlannerRequest request) {
            result.setReturnValue(ECOPlanningService.begin(level, grid, requester.getActionSource(), key,
                    amount, strategy, request.neoecoae$getPlannerOptions()));
        }
    }
}
