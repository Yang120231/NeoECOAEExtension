package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProcessingExecutionPattern;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.gtocore.testing.GtoPatternBuffer;
import com.gtocore.testing.GtoWildcardPatternBuffer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOGtoBufferCapacityTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void itemAndFluidCapacityUseLiveNativeSimulationWithoutMovingResources() {
        var pattern = mock(IPatternDetails.class);
        var buffer = new GtoPatternBuffer(); buffer.bind(pattern);
        var ore = item("ore");
        var water = mock(AEFluidKey.class);
        when(water.getType()).thenReturn(AEKeyType.fluids());
        var items = new KeyCounter(); items.add(ore, 3);
        var fluid = new KeyCounter(); fluid.add(water, 5);
        buffer.stored.add(ore, Long.MAX_VALUE - 30);
        buffer.stored.add(water, Long.MAX_VALUE - 20);
        assertTrue(ECOGtoProcessingDispatch.isBuffer(buffer));
        assertEquals(4, ECOGtoProcessingDispatch.maxOperations(buffer, pattern, new KeyCounter[]{items, fluid}, 100));
        assertEquals(Long.MAX_VALUE - 30, buffer.stored.get(ore));
        assertEquals(Long.MAX_VALUE - 20, buffer.stored.get(water));
        buffer.stored.add(water, 20);
        assertEquals(0, ECOGtoProcessingDispatch.maxOperations(buffer, pattern, new KeyCounter[]{items, fluid}, 100));
    }

    @Test void duplicateSlotsShareOneCapacityAndAnUnknownPatternCannotDebitInputs() {
        var pattern = mock(IPatternDetails.class);
        var buffer = new GtoPatternBuffer(); buffer.bind(pattern);
        var ore = item("ore");
        var first = new KeyCounter(); first.add(ore, 2);
        var second = new KeyCounter(); second.add(ore, 3);
        buffer.stored.add(ore, Long.MAX_VALUE - 24);
        assertEquals(4, ECOGtoProcessingDispatch.maxOperations(buffer, pattern, new KeyCounter[]{first, second}, 100));
        assertEquals(0, ECOGtoProcessingDispatch.maxOperations(buffer, mock(IPatternDetails.class), new KeyCounter[]{first}, 1));
        assertEquals(0, buffer.pushes);
    }

    @Test void programmedCircuitIsSentOnceAndDoesNotDivideTheCraftCapacity() {
        var pattern = mock(IPatternDetails.class);
        var buffer = new GtoPatternBuffer(); buffer.bind(pattern);
        var circuit = item("programmed_circuit");
        var inputs = new KeyCounter(); inputs.add(circuit, 1);
        buffer.stored.add(circuit, Long.MAX_VALUE - 1);
        assertEquals(1, ECOGtoProcessingDispatch.inputScaling(buffer).multiplier(circuit, 5000));
        assertEquals(5000, ECOGtoProcessingDispatch.maxOperations(buffer, pattern, new KeyCounter[]{inputs}, 5000));
        buffer.stored.add(circuit, 1);
        assertEquals(0, ECOGtoProcessingDispatch.maxOperations(buffer, pattern, new KeyCounter[]{inputs}, 5000));
    }

    @Test void canonicalAdmissionEmitsCompleteItemFluidTotalsAndOneCircuit() {
        var pattern = mock(AEProcessingPattern.class);
        var buffer = new GtoPatternBuffer(); buffer.bind(pattern);
        var ore = item("ore");
        var circuit = item("programmed_circuit");
        var water = mock(AEFluidKey.class);
        when(water.getType()).thenReturn(AEKeyType.fluids());
        doAnswer(call -> {
            IPatternDetails.PatternInputSink sink = call.getArgument(1);
            sink.pushInput(ore, 3); sink.pushInput(water, 5); sink.pushInput(circuit, 1);
            return null;
        }).when(pattern).pushInputsToExternalInventory(any(), any());
        var total = new KeyCounter(); total.add(ore, 192); total.add(water, 320); total.add(circuit, 1);
        var execution = new ECOProcessingExecutionPattern(pattern, 64, ECOGtoProcessingDispatch.inputScaling(buffer));
        assertTrue(ECOGtoProcessingDispatch.pushBuffer(buffer, pattern, execution, new KeyCounter[]{total}));
        assertEquals(1, buffer.pushes);
        assertEquals(192, buffer.stored.get(ore));
        assertEquals(320, buffer.stored.get(water));
        assertEquals(1, buffer.stored.get(circuit));
        assertFalse(ECOGtoProcessingDispatch.emitBatch(pattern, new KeyCounter[]{total}, (key, amount) -> fail()));
    }

    @Test void generatedWildcardPatternsUseTheNativeSharedSlotRatherThanTheTemplateMap() {
        var pattern = mock(IPatternDetails.class);
        var buffer = new GtoWildcardPatternBuffer(); buffer.bind(pattern);
        var ore = item("ore");
        var inputs = new KeyCounter(); inputs.add(ore, 3);
        buffer.stored.add(ore, Long.MAX_VALUE - 30);
        assertTrue(ECOGtoProcessingDispatch.isBuffer(buffer));
        assertEquals(10, ECOGtoProcessingDispatch.maxOperations(buffer, pattern, new KeyCounter[]{inputs}, 100));
        buffer.stored.add(ore, 30);
        assertEquals(0, ECOGtoProcessingDispatch.maxOperations(buffer, pattern, new KeyCounter[]{inputs}, 1));
    }

    private static AEItemKey item(String path) {
        var key = mock(AEItemKey.class);
        when(key.getType()).thenReturn(AEKeyType.items());
        when(key.getId()).thenReturn(new ResourceLocation("gtceu", path));
        return key;
    }
}
