package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.*;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.api.me.provider.*;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ECOExternalCpuFastPathTest {
    @BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }
    interface FastProvider extends ICraftingProvider, ECOFastPathDispatchProvider {}
    interface ParallelProvider extends ICraftingProvider, ECOParallelCraftingProvider {}
    final AEKey input = mock(AEKey.class, RETURNS_DEEP_STUBS);
    final AEKey output = mock(AEKey.class, RETURNS_DEEP_STUBS);
    final AEKey container = mock(AEKey.class, RETURNS_DEEP_STUBS);
    final IPatternDetails pattern = mock(IPatternDetails.class, RETURNS_DEEP_STUBS);
    final IEnergyService energy = mock(IEnergyService.class);
    final CraftingService service = mock(CraftingService.class);
    final Level level = mock(Level.class);
    final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
    final ListCraftingInventory waiting = new ListCraftingInventory(ignored -> {});
    final ECOExternalCpuFastPath dispatcher = new ECOExternalCpuFastPath(() -> {});
    final FastProvider provider = mock(FastProvider.class);
    final AtomicLong remaining = new AtomicLong(1024);
    final Map<IPatternDetails, Object> tasks = new LinkedHashMap<>();
    final UUID jobId = UUID.randomUUID();
    final List<ECOFastPathDispatchProvider.Batch> deliveries = new ArrayList<>();
    ECOExternalCpuJob job;
    boolean reject, uncertain, ordinaryFailure;

    int dispatch(int pushSlots) {
        // Recipe extraction is stubbed; facade, planner, material lease, energy and accounting are real.
        try (var helper = mockStatic(CraftingCpuHelper.class)) {
            helper.when(() -> CraftingCpuHelper.extractPatternInputs(eq(pattern), any(), eq(level), any(), any()))
                    .thenAnswer(c -> {
                        ListCraftingInventory preview = c.getArgument(1);
                        if (preview.extract(input, 2, Actionable.MODULATE) != 2) return null;
                        ((KeyCounter) c.getArgument(3)).add(output, 2);
                        ((KeyCounter) c.getArgument(4)).add(container, 1);
                        var counter = new KeyCounter(); counter.add(input, 2);
                        return new KeyCounter[]{counter};
                    });
            helper.when(() -> CraftingCpuHelper.calculatePatternPower(any())).thenReturn(1.0);
            return dispatcher.execute(this, job, inventory, pushSlots, service, energy, level);
        }
    }

}
