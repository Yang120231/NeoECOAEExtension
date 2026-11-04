package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(cn.dancingsnow.neoecoae.util.GtoNativeKeyExtension.class)
class LargeWorkstationTerminalGroupTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("cn.dancingsnow.neoecoae.NeoECOAE");
        }
    }

    @Test
    void detachedInterfacesAndClustersWithoutControllersUseTheRegularFallback() {
        var host = host(new AtomicReference<>(LargeWorkstationModule.REGULAR));
        var regular = host.getTerminalGroup();
        doReturn(null).when(host).getCluster();
        assertEquals(regular, host.getTerminalGroup());
        doReturn(mock(NEIntegratedWorkingStationCluster.class)).when(host).getCluster();
        assertEquals(regular, host.getTerminalGroup());
    }

    @Test
    void terminalIconModelUsesTheNativeControllerArtwork() throws Exception {
        try (var stream = getClass().getResourceAsStream(
            "/assets/neoecoae/models/item/large_integrated_working_station_interface.json")) {
            assertNotNull(stream);
            var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("neoecoae:block/large_integrated_working_station_interface", model.get("parent").getAsString());
            assertFalse(model.has("overrides"), "Native icon must not retain retired addon model overrides");
        }
    }

    private static ECOLargeIntegratedWorkingStationInterfaceBlockEntity host(
        AtomicReference<LargeWorkstationModule> module
    ) {
        var host = mock(ECOLargeIntegratedWorkingStationInterfaceBlockEntity.class, CALLS_REAL_METHODS);
        var controller = mock(ECOLargeIntegratedWorkingStationBlockEntity.class);
        var cluster = mock(NEIntegratedWorkingStationCluster.class);
        when(controller.getInstalledModule()).thenAnswer(call -> module.get());
        when(cluster.getController()).thenReturn(controller);
        doReturn(cluster).when(host).getCluster();
        doReturn(Blocks.BARREL.defaultBlockState()).when(host).getBlockState();
        doReturn(Component.literal("Large Integrated Working Station Communication Interface")).when(host).getName();
        return host;
    }
}
