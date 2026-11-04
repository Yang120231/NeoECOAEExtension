package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.Ae2CraftingCpuFastPathMixin;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class ECOExternalCpuSupportTest {
    @BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }
    @AfterEach void clearRegistry() { ECOPlanningResultRegistry.clear(); }

    CraftingPlan plan() {
        return new CraftingPlan(new GenericStack(AEItemKey.of(Items.STONE), 512), 128,
                false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
    }

    @Test void lostMetadataDoesNotSilentlyStripEcoRuntime() {
        var plan = plan();
        try (var registry = mockStatic(ECOPlanningResultRegistry.class)) {
            registry.when(() -> ECOPlanningResultRegistry.isECOOwnedPlan(plan)).thenReturn(true);
            assertFalse(ECOExternalCpuSupport.supportsPlan(plan));
        }
    }

    @Test void longProjectionCannotHideExactMaterialOverflow() {
        var plan = plan();
        var result = mock(ECOPlanningResult.class);
        when(result.exactUsedItems()).thenReturn(Map.of(AEItemKey.of(Items.STONE),
                PlannerAmount.of(BigInteger.ONE.shiftLeft(64))));
        var contract = ECOExecutionContract.nativeContract(UUID.randomUUID(),
                cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity.of(plan));
        try (var registry = mockStatic(ECOPlanningResultRegistry.class)) {
            registry.when(() -> ECOPlanningResultRegistry.isECOOwnedPlan(plan)).thenReturn(true);
            registry.when(() -> ECOPlanningResultRegistry.find(plan)).thenReturn(result);
            registry.when(() -> ECOPlanningResultRegistry.resolveContract(plan, null)).thenReturn(contract);
            assertFalse(ECOExternalCpuSupport.supportsPlan(plan));
        }
    }


}
