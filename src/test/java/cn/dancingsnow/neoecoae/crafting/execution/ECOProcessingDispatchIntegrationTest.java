package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.compat.ae2.ECOExternalProviderBlocking;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class ECOProcessingDispatchIntegrationTest {
    @Test
    void scaledProcessingRemaindersRemainInTheWaitingLedgerForEveryAcceptedChunk() {
        var f = new Fixture();
        f.request.remainders().add(f.key, 1);
        var progress = new ExecutingCraftingJob.TaskProgress();
        progress.value = 16;
        f.request.job().tasks.put(f.request.pattern(), progress);
        var accounting = new ECOCraftingDispatchAccounting(ignored -> {}, () -> {},
            current -> new cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext(
                mock(appeng.api.networking.crafting.ICraftingCPU.class), current.link.getCraftingID(),
                current.finalOutput, 16, current.remainingAmount), ignored -> {});
        var dispatcher = new ECOProcessingPatternDispatcher(null,
            new ECOCraftingEnergyTransaction(() -> {}, () -> 0), accounting);
        dispatcher.beginTick(0);
        var offers = new ArrayList<Long>();
        var result = dispatcher.tryScaledDispatch(f.request, f.provider, 1, f.energy, ignored -> {},
            (request, provider) -> { offers.add(request.allowedCrafts()); return true; });
        assertEquals(List.of(1L, 2L, 4L, 8L, 1L), offers);
        assertEquals(16, result.acceptedCrafts());
        assertEquals(16, cn.dancingsnow.neoecoae.platform.JdkCompat.first(result.remainders()).amount());
        assertEquals(32, f.request.job().waitingFor.list.get(f.key));
        assertEquals(0, progress.value);
        assertEquals(84, f.inventory.list.get(f.key));
    }

    @Test
    void ordinaryRampGrowsWithinColdVisitAndDebitsOnlyAcceptedChunks() {
        var f = new Fixture();
        var offers = new ArrayList<Long>();
        var result = f.scaled(0, (request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        });
        assertEquals(List.of(1L, 2L, 4L, 8L, 1L), offers);
        assertEquals(16, result.acceptedCrafts());
        assertEquals(84, f.inventory.list.get(f.key));
        verify(f.accounting, times(5)).apply(eq(f.request), any(), any(), eq(f.provider));
        offers.clear();
        var growth = f.scaled(5, (request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        });
        assertEquals(List.of(16L), offers);
        assertEquals(16, growth.acceptedCrafts());
    }

    @Test
    void warmedRampRefundsRejectedChunksAndStopsAfterRecovery() {
        var f = new Fixture();
        f.scaled(0, (request, provider) -> true);
        var offers = new ArrayList<Long>();
        var result = f.scaled(5, (request, provider) -> {
            offers.add(request.allowedCrafts());
            return request.allowedCrafts() <= 2;
        });
        assertEquals(List.of(16L, 8L, 4L, 2L), offers);
        assertEquals(2, result.acceptedCrafts());
        assertEquals(82, f.inventory.list.get(f.key));
    }

    @Test
    void bufferedAcceptanceStopsGrowthAndRemainsOwned() {
        var f = new Fixture();
        f.scaled(0, (request, provider) -> true);
        when(((PatternProviderLogicAccessor) f.provider).neoecoae$getSendList())
                .thenReturn(List.of(new GenericStack(f.key, 1)));
        var offers = new ArrayList<Long>();
        var result = f.scaled(5, (request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        });
        assertEquals(List.of(16L), offers);
        assertEquals(16, result.acceptedCrafts());
        assertEquals(68, f.inventory.list.get(f.key));
        when(((PatternProviderLogicAccessor) f.provider).neoecoae$getSendList()).thenReturn(List.of());
        offers.clear();
        f.scaled(10, (request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        });
        assertEquals(List.of(16L), offers);
    }

    @Test
    void fullInventoryStopsAfterOneRejectionAndRetainsTheSuccessfulChunk() {
        var f = new Fixture(1_000_000, 2_000_000);
        assertEquals(127, f.scaled(0, 127, (request, provider) -> true).acceptedCrafts());
        var offers = new ArrayList<Long>();
        long[] free = {128};
        var result = f.scaled(0, (request, provider) -> {
            offers.add(request.allowedCrafts());
            if (free[0] < request.allowedCrafts()) return false;
            free[0] -= request.allowedCrafts();
            return true;
        });
        assertEquals(128, result.acceptedCrafts());
        assertEquals(List.of(64L, 64L, 64L), offers);
        assertEquals(2_000_000 - 127 - 128, f.inventory.list.get(f.key));
        assertNull(f.scaled(0, (request, provider) -> fail("Saturated target must wait for the next tick")));
        offers.clear();
        assertEquals(64, f.scaled(1, 64, (request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        }).acceptedCrafts());
        assertEquals(List.of(64L), offers);
    }

    @Test
    void saturatedProviderDoesNotReceiveAnOrdinaryFallbackInTheSameTick() {
        var f = new Fixture(1_000_000, 2_000_000);
        var outer = f.outerDispatcher();
        outer.beginTick(0);
        long[] free = {127};
        int[] calls = {0};
        ECOCraftingProviderDispatcher.ECOCraftingNormalPush push = (request, provider) -> {
            calls[0]++;
            if (free[0] < request.allowedCrafts()) return false;
            free[0] -= request.allowedCrafts();
            return true;
        };
        assertEquals(127, f.outerDispatch(outer, push).acceptedCrafts());
        int attempted = calls[0];
        assertFalse(f.outerDispatch(outer, push).accepted());
        assertEquals(attempted, calls[0]);
        assertEquals(0, free[0]);
    }

    @Test
    void tinyWarmBatchHasSharedAttemptBudgetAndRecoversExponentiallyNextTick() {
        var f = new Fixture(1_000_000, 2_000_000);
        f.scaled(0, 1, (request, provider) -> true);
        int[] calls = {0};
        ECOCraftingProviderDispatcher.ECOCraftingNormalPush push = (request, provider) -> {
            calls[0]++;
            assertEquals(1, request.allowedCrafts());
            return true;
        };
        // One pattern owns at most its per-tick share of submissions; the probe above already spent one.
        assertEquals(ECOProcessingPatternDispatcher.MAX_ATTEMPTS_PER_PATTERN_PER_TICK - 1,
                f.scaled(0, push).acceptedCrafts());
        assertNull(f.scaled(0, push));
        assertEquals(ECOProcessingPatternDispatcher.MAX_ATTEMPTS_PER_PATTERN_PER_TICK - 1, calls[0]);
        assertEquals(2_000_000 - ECOProcessingPatternDispatcher.MAX_ATTEMPTS_PER_PATTERN_PER_TICK,
                f.inventory.list.get(f.key));

        calls[0] = 0;
        var result = f.scaled(1, (request, provider) -> { calls[0]++; return true; });
        assertEquals(1_000_000, result.acceptedCrafts());
        assertTrue(calls[0] <= 20, "Successful recovery should reach a million copies in logarithmic calls");
    }

    @Test
    void exhaustedBudgetCannotFallThroughToUnbudgetedOrdinaryPushes() {
        var f = new Fixture(1, 1000);
        var outer = f.outerDispatcher();
        outer.beginTick(0);
        int[] calls = {0};
        for (int i = 0; i < ECOProcessingPatternDispatcher.MAX_ATTEMPTS_PER_PATTERN_PER_TICK; i++) {
            assertTrue(f.outerDispatch(outer, (request, provider) -> { calls[0]++; return true; }).accepted());
        }
        assertFalse(f.outerDispatch(outer, (request, provider) -> { calls[0]++; return true; }).accepted());
        assertEquals(ECOProcessingPatternDispatcher.MAX_ATTEMPTS_PER_PATTERN_PER_TICK, calls[0]);
        outer.beginTick(1);
        assertTrue(f.outerDispatch(outer, (request, provider) -> true).accepted());
    }

    @Test
    void firstScaledRejectionStillGetsOneOrdinaryFallback() {
        var f = new Fixture();
        var outer = f.outerDispatcher();
        outer.beginTick(0);
        int[] calls = {0};
        var result = f.outerDispatch(outer, (request, provider) -> ++calls[0] == 2);
        assertTrue(result.accepted());
        assertEquals(1, result.acceptedCrafts());
        assertEquals(2, calls[0]);
        assertEquals(99, f.inventory.list.get(f.key));
        var next = f.outerDispatch(outer, (request, provider) -> {
            calls[0]++;
            return true;
        });
        assertTrue(next.accepted(), "A live single-copy provider can continue during scaled cooldown");
        assertEquals(3, calls[0]);
    }

    @Test
    void planRefreshesMaterialAndWaitingLimitsAfterProviderCallbacks() {
        for (boolean exhaustMaterials : new boolean[]{false, true}) {
            var f = new Fixture();
            int[] calls = {0};
            var result = f.scaled(0, (request, provider) -> {
                calls[0]++;
                if (exhaustMaterials) f.inventory.extract(f.key, 100, Actionable.MODULATE);
                else f.request.job().waitingFor.insert(f.key, Long.MAX_VALUE, Actionable.MODULATE);
                return true;
            });
            assertEquals(1, result.acceptedCrafts());
            assertEquals(1, calls[0]);
        }
    }

    @Test
    void advancedProviderAcceptsEcoScaledOrdinaryPattern() {
        var f = new Fixture();
        var provider = mock(AdvPatternProviderLogic.class);
        assertTrue(ECOProcessingPatternDispatcher.supportsScaledDispatch(f.request, provider));
        f.dispatcher.beginTick(0);
        var result = f.dispatcher.tryScaledDispatch(f.request, provider, 1, f.energy, ignored -> {},
                (request, ignored) -> {
                    assertTrue(List.of(f.request.pattern()).contains(request.pattern()));
                    assertEquals(request.allowedCrafts(), cn.dancingsnow.neoecoae.platform.JdkCompat.first(request.pattern().getOutputs()).amount());
                    assertEquals(request.allowedCrafts(), request.inputs()[0].get(f.key));
                    return true;
                });
        assertEquals(16, result.acceptedCrafts());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }

    private static final class Fixture {
        final AEKey key = mock(AEKey.class);
        final IEnergyService energy = mock(IEnergyService.class);
        final ECOCraftingDispatchAccounting accounting = mock(ECOCraftingDispatchAccounting.class);
        final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
        final PatternProviderLogic provider = mock(PatternProviderLogic.class,
                withSettings().extraInterfaces(PatternProviderLogicAccessor.class));
        final ECOProcessingPatternDispatcher dispatcher = new ECOProcessingPatternDispatcher(null,
                new ECOCraftingEnergyTransaction(() -> {}, () -> 0), accounting);
        final ECOCraftingDispatchRequest request;

        Fixture() { this(16, 100); }

        Fixture(long requested, long stock) {
            InventoryTestBootstrap.initialize();
            var keyType = mock(AEKeyType.class);
            when(key.getType()).thenReturn(keyType);
            when(keyType.getId()).thenReturn(new ResourceLocation("test", "input"));
            var pattern = mock(AEProcessingPattern.class);
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.STONE));
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{mock(IPatternDetails.IInput.class)});
            when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(key, 1)});
            when(pattern.supportsPushInputsToExternalInventory()).thenReturn(true);
            when(((PatternProviderLogicAccessor) provider).neoecoae$getSendList()).thenReturn(List.of());
            when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(i -> i.getArgument(0));
            var inputs = new KeyCounter();
            inputs.add(key, 1);
            var plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(key, requested));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            var link = mock(CraftingLink.class);
            when(link.getCraftingID()).thenReturn(java.util.UUID.randomUUID());
            ExecutingCraftingJob job;
            try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
                job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
            }
            request = new ECOCraftingDispatchRequest(job, null, pattern, new KeyCounter[]{inputs},
                    inputs, new KeyCounter(), requested, inventory, mock(net.minecraft.world.level.Level.class));
            inventory.insert(key, stock, Actionable.MODULATE);
        }

        ECOCraftingDispatchResult scaled(long tick, ECOCraftingProviderDispatcher.ECOCraftingNormalPush push) {
            return scaled(tick, request.allowedCrafts(), push);
        }

        ECOCraftingDispatchResult scaled(long tick, long limit, ECOCraftingProviderDispatcher.ECOCraftingNormalPush push) {
            dispatcher.beginTick(tick);
            var limited = limit == request.allowedCrafts() ? request : new ECOCraftingDispatchRequest(
                    request.job(), request.candidate(), request.pattern(), request.inputs(), request.outputs(),
                    request.remainders(), limit, inventory, request.level());
            return dispatcher.tryScaledDispatch(limited, provider, 1, energy, ignored -> {}, push);
        }

        ECOCraftingProviderDispatcher outerDispatcher() {
            return new ECOCraftingProviderDispatcher(null, mock(ECOCraftingFastPathDispatcher.class),
                    new ECOCraftingEnergyTransaction(() -> {}, () -> 0), accounting);
        }

        ECOCraftingProviderDispatcher.Result outerDispatch(ECOCraftingProviderDispatcher outer,
                ECOCraftingProviderDispatcher.ECOCraftingNormalPush push) {
            try (var helper = mockStatic(appeng.crafting.execution.CraftingCpuHelper.class)) {
                helper.when(() -> appeng.crafting.execution.CraftingCpuHelper.calculatePatternPower(any())).thenReturn(1.0);
                return outer.dispatchCandidate(request, List.of(provider), new ECOCraftingDispatchBudget(64, 64),
                        energy, mock(ECODispatchStallDiagnostics.class), ignored -> {}, () -> {}, push);
            }
        }
    }
}
