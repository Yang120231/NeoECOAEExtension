package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(cn.dancingsnow.neoecoae.util.GtoNativeKeyExtension.class)
class LargeWorkstationExtraInputsTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void partialExtractionIsOwnedAndRetryOnlyRequestsRemainder() {
        var storage = mock(MEStorage.class);
        var source = mock(IActionSource.class);
        var missing = new KeyCounter();
        var owned = new KeyCounter();
        var changes = new AtomicInteger();
        missing.add(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 28);
        when(storage.extract(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 28, Actionable.MODULATE, source)).thenReturn(10L);
        when(storage.extract(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 18, Actionable.MODULATE, source)).thenReturn(18L);
        assertFalse(LargeWorkstationExtraInputs.acquire(missing, owned, storage, source, changes::incrementAndGet));
        assertEquals(10, owned.get(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT)));
        assertEquals(18, missing.get(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT)));
        assertTrue(LargeWorkstationExtraInputs.acquire(missing, owned, storage, source, changes::incrementAndGet));
        assertEquals(28, owned.get(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT)));
        assertEquals(2, changes.get());
        assertTrue(LargeWorkstationExtraInputs.acquire(missing, owned, storage, source, changes::incrementAndGet));
        verify(storage).extract(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 28, Actionable.MODULATE, source);
        verify(storage).extract(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 18, Actionable.MODULATE, source);
        verifyNoMoreInteractions(storage);
    }

    @Test void missingLightningNeverCreatesOwnedResources() {
        var missing = new KeyCounter();
        var owned = new KeyCounter();
        missing.add(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 4096);
        assertFalse(LargeWorkstationExtraInputs.acquire(missing, owned, mock(MEStorage.class), mock(IActionSource.class),
            () -> fail("No mutation should be saved")));
        assertEquals(0, owned.get(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT)));
        assertEquals(4096, missing.get(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT)));
    }

    @Test void returningOwnedInputsNeverInsertsUnacquiredExtras() {
        var storage = mock(MEStorage.class);
        var source = mock(IActionSource.class);
        var owned = new KeyCounter();
        var stillMissing = new KeyCounter();
        owned.add(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 10);
        stillMissing.add(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 18);
        when(storage.insert(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 10, Actionable.MODULATE, source)).thenReturn(10L);

        assertTrue(LargeWorkstationExtraInputs.returnOwned(owned, storage, source, () -> {}));

        assertEquals(0, owned.get(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT)));
        assertEquals(18, stillMissing.get(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT)));
        verify(storage).insert(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 10, Actionable.MODULATE, source);
        verifyNoMoreInteractions(storage);
    }

    @Test void partialReturnKeepsOnlyTheUninsertedOwnedAmount() {
        var storage = mock(MEStorage.class);
        var source = mock(IActionSource.class);
        var owned = new KeyCounter();
        var changes = new AtomicInteger();
        owned.add(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 10);
        when(storage.insert(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 10, Actionable.MODULATE, source)).thenReturn(4L);
        when(storage.insert(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 6, Actionable.MODULATE, source)).thenReturn(6L);

        assertFalse(LargeWorkstationExtraInputs.returnOwned(owned, storage, source, changes::incrementAndGet));
        assertEquals(6, owned.get(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT)));
        assertTrue(LargeWorkstationExtraInputs.returnOwned(owned, storage, source, changes::incrementAndGet));
        assertEquals(0, owned.get(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT)));
        assertEquals(2, changes.get());
        verify(storage).insert(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 10, Actionable.MODULATE, source);
        verify(storage).insert(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 6, Actionable.MODULATE, source);
        verifyNoMoreInteractions(storage);
    }
}
