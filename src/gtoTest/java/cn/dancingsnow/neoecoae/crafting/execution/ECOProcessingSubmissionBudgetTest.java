package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * GTO processing budget: submissions are counted, not crafts. Negotiated-capacity buffers receive the whole ready
 * remainder in one canonical-pattern push; probing providers keep their per-pattern and per-tick call limits.
 */
class ECOProcessingSubmissionBudgetTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void countedBufferTakesMoreThanIntegerMaxCopiesInOneCanonicalPatternSubmission() {
        long crafts = 5_000_000_000L;
        var f = new Fixture(crafts);
        var buffer = f.buffer();
        var result = f.dispatch(buffer, (request, target) -> fail("Native buffer admission owns the transport"));
        assertEquals(crafts, result.acceptedCrafts());
        assertEquals(1, buffer.pushes);
        assertEquals(3 * crafts, buffer.stored.get(f.ore));
        assertEquals(0, f.inventory.list.get(f.ore));
        assertEquals(0, f.progress.value);
        assertEquals(2 * crafts, f.job.waitingFor.list.get(f.output));
    }

    @Test void theFormerMillionCraftBudgetNoLongerSplitsACountedBatch() {
        var f = new Fixture(1_000_001);
        var buffer = f.buffer();
        assertEquals(1_000_001, f.dispatch(buffer, (request, target) -> false).acceptedCrafts());
        assertEquals(1, buffer.pushes);
        assertEquals(0, f.progress.value);
    }

    @Test void bufferCapacityNotCraftCountSplitsSubmissionsAcrossTicks() {
        long crafts = 3_000_000_000L;
        var f = new Fixture(crafts);
        var buffer = f.buffer();
        buffer.stored.add(f.ore, Long.MAX_VALUE - 3 * 1_000_000_000L);
        assertEquals(1_000_000_000L, f.dispatch(buffer, (request, target) -> false).acceptedCrafts());
        assertTrue(f.dispatcher.isScaledFallbackDeferred(f.request, buffer));
        assertFalse(f.dispatcher.isScaledAttemptBudgetExhausted(f.pattern), "capacity is not budget");
        f.dispatcher.beginTick(1);
        buffer.stored.add(f.ore, -Long.MAX_VALUE + 3 * 1_000_000_000L);
        assertEquals(2_000_000_000L, f.dispatch(buffer, (request, target) -> false).acceptedCrafts());
        assertEquals(0, f.inventory.list.get(f.ore));
        assertEquals(2 * crafts, f.job.waitingFor.list.get(f.output));
    }

    @Test void rejectedBatchRefundsInputsAndUncertainCustodyNeverReplays() {
        var f = new Fixture(4_000_000_000L);
        var buffer = f.buffer();
        buffer.reject = true;
        assertNull(f.dispatch(buffer, (request, target) -> fail("Rejection must not use a scaled wrapper")));
        assertEquals(3 * 4_000_000_000L, f.inventory.list.get(f.ore));
        assertEquals(4_000_000_000L, f.progress.value);
        assertFalse(f.job.suspended);
        verify(f.energy, atLeastOnce()).injectPower(anyDouble(), eq(Actionable.MODULATE));

        // A real rejection backs the provider off; an exhausted budget (tested below) does not.
        f.dispatcher.beginTick(1);
        assertTrue(f.dispatcher.isScaledDispatchDeferred(f.request, buffer));

        var g = new Fixture(4_000_000_000L);
        var failing = g.buffer();
        failing.failAfterInsertion = true;
        assertNull(g.dispatch(failing, (request, target) -> fail("Uncertain custody must never be replayed")));
        assertTrue(g.job.suspended);
        assertEquals(1, failing.pushes);
        assertEquals(0, g.inventory.list.get(g.ore), "the target may own the inputs: no refund");
        assertEquals(3 * 4_000_000_000L, failing.stored.get(g.ore));
    }

    @Test void probingProvidersSpendAtMostTheirPatternShareOfSubmissionsPerTick() {
        var f = new Fixture(1L << 40);
        var provider = mock(PatternProviderLogic.class, withSettings().extraInterfaces(PatternProviderLogicAccessor.class));
        when(((PatternProviderLogicAccessor) provider).neoecoae$getSendList()).thenReturn(List.of());
        int[] pushes = {0};
        ECOCraftingProviderDispatcher.ECOCraftingNormalPush push = (request, target) -> {
            pushes[0]++;
            assertTrue(request.allowedCrafts() <= Integer.MAX_VALUE, "probing views stay below the int ceiling");
            return true;
        };
        while (f.dispatch(provider, push) != null) {
            assertTrue(pushes[0] <= ECOProcessingPatternDispatcher.MAX_ATTEMPTS_PER_TICK);
        }
        assertEquals(ECOProcessingPatternDispatcher.MAX_ATTEMPTS_PER_PATTERN_PER_TICK, pushes[0]);
        assertTrue(f.dispatcher.isScaledAttemptBudgetExhausted(f.pattern));
        assertFalse(f.dispatcher.isScaledAttemptBudgetExhausted(), "other patterns keep half of the tick");
        assertFalse(f.dispatcher.mayAttemptBatch(provider, f.pattern));
        assertFalse(f.dispatcher.isScaledDispatchDeferred(f.request, provider), "budget is not provider backoff");
        f.dispatcher.beginTick(1);
        assertTrue(f.dispatcher.mayAttemptBatch(provider, f.pattern));
        assertNotNull(f.dispatch(provider, push));
    }

    @Test void resetClearsDeferralsAndSpentShares() {
        var f = new Fixture(64);
        var buffer = f.buffer();
        buffer.stored.add(f.ore, Long.MAX_VALUE);
        assertNull(f.dispatch(buffer, (request, target) -> false));
        assertTrue(f.dispatcher.isScaledDispatchDeferred(f.request, buffer));
        f.dispatcher.reset();
        f.dispatcher.beginTick(0);
        assertFalse(f.dispatcher.isScaledDispatchDeferred(f.request, buffer));
        buffer.stored.add(f.ore, -Long.MAX_VALUE);
        assertEquals(64, f.dispatch(buffer, (request, target) -> false).acceptedCrafts());
    }

    /** One craft turns 3 ore into 2 output. */
    private static final class Fixture {
        final AEItemKey ore = itemKey("ore");
        final AEItemKey output = itemKey("output");
        final IPatternDetails pattern = mock(AEProcessingPattern.class);
        final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
        final IEnergyService energy = mock(IEnergyService.class);
        final ExecutingCraftingJob.TaskProgress progress = new ExecutingCraftingJob.TaskProgress();
        final ExecutingCraftingJob job;
        final ECOCraftingDispatchRequest request;
        final ECOProcessingPatternDispatcher dispatcher;

        Fixture(long crafts) {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(ore, 1)});
            when(input.getMultiplier()).thenReturn(3L);
            var inputs = new KeyCounter();
            inputs.add(ore, 3);
            inventory.insert(ore, 3 * crafts, Actionable.MODULATE);
            when(pattern.getDefinition()).thenReturn(mock(AEItemKey.class, RETURNS_DEEP_STUBS));
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
            when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(output, 2)});
            when(pattern.supportsPushInputsToExternalInventory()).thenReturn(true);
            doAnswer(call -> {
                IPatternDetails.PatternInputSink sink = call.getArgument(1);
                sink.pushInput(ore, 3);
                return null;
            }).when(pattern).pushInputsToExternalInventory(any(), any());
            when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
            var plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(output, crafts * 2L));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            // The GTO fork exposes pattern times as a primitive map; the job ledger is seeded explicitly below.
            when(plan.patternTimes()).thenReturn(new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<>());
            var link = mock(CraftingLink.class);
            when(link.getCraftingID()).thenReturn(UUID.randomUUID());
            try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
                job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
            }
            progress.value = crafts;
            job.tasks.put(pattern, progress);
            var outputs = new KeyCounter();
            outputs.add(output, 2);
            request = new ECOCraftingDispatchRequest(job, null, pattern, new KeyCounter[]{inputs}, outputs,
                    new KeyCounter(), crafts, inventory, mock(Level.class));
            var accounting = new ECOCraftingDispatchAccounting(ignored -> {}, () -> {},
                    current -> new ECOCraftingJobContext(mock(ICraftingCPU.class), link.getCraftingID(),
                            current.finalOutput, crafts * 2L, current.remainingAmount), ignored -> {});
            dispatcher = new ECOProcessingPatternDispatcher(null,
                    new ECOCraftingEnergyTransaction(() -> {}, () -> 0), accounting);
            dispatcher.beginTick(0);
        }

        private static AEItemKey itemKey(String name) {
            var key = mock(AEItemKey.class);
            when(key.getType()).thenReturn(AEKeyType.items());
            when(key.getId()).thenReturn(new ResourceLocation("neoecoae_test", name));
            return key;
        }

        MEPatternBufferPartMachineBase buffer() {
            var buffer = new MEPatternBufferPartMachineBase();
            buffer.pattern = pattern;
            return buffer;
        }

        ECOCraftingDispatchResult dispatch(appeng.api.networking.crafting.ICraftingProvider provider,
                ECOCraftingProviderDispatcher.ECOCraftingNormalPush push) {
            return dispatcher.tryScaledDispatch(request, provider, 1, energy, ignored -> {}, push);
        }
    }
}
