package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.menu.MenuOpener;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.computation.ECOCraftingStatusUI;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GtoUiContractsTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void installedGtoMenuOpenerExposesTheStatusInterceptionPoint() throws Exception {
        try (var input = MenuOpener.class.getResourceAsStream("MenuOpener.class")) {
            assertNotNull(input);
            var type = new ClassNode();
            new ClassReader(input).accept(type, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            assertTrue(type.methods.stream().anyMatch(method -> method.name.equals("open")
                && method.desc.equals("(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/world/entity/player/Player;Lappeng/menu/locator/MenuLocator;Z)Z")
                && (method.access & Opcodes.ACC_STATIC) != 0));
        }
    }

    @Test void terminalStatusReplacementRequiresAFormedOnlineActiveHost() {
        var grid = mock(IGrid.class);
        var host = mock(ECOComputationSystemBlockEntity.class);
        var cluster = mock(NEComputationCluster.class);
        var node = mock(IManagedGridNode.class);
        when(grid.getMachineClasses()).thenReturn(List.of(ECOComputationSystemBlockEntity.class));
        when(grid.getMachines(ECOComputationSystemBlockEntity.class)).thenReturn(Set.of(host));
        when(host.getMainNode()).thenReturn(node);
        when(host.getCluster()).thenReturn(cluster);
        assertFalse(ECOCraftingStatusUI.hasFunctionalHost(grid));
        when(host.isFormed()).thenReturn(true);
        assertFalse(ECOCraftingStatusUI.hasFunctionalHost(grid));
        when(node.isOnline()).thenReturn(true);
        assertFalse(ECOCraftingStatusUI.hasFunctionalHost(grid));
        when(cluster.isActive()).thenReturn(true);
        assertTrue(ECOCraftingStatusUI.hasFunctionalHost(grid));
        when(node.isOnline()).thenReturn(false);
        assertFalse(ECOCraftingStatusUI.hasFunctionalHost(grid));
    }

    @Test void networksWithoutComputationHostsKeepTheNativeStatusMenu() {
        var grid = mock(IGrid.class);
        when(grid.getMachineClasses()).thenReturn(List.of());
        assertFalse(ECOCraftingStatusUI.hasFunctionalHost(grid));
    }
}
