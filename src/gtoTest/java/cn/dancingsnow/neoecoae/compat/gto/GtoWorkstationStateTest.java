package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.Test;

class GtoWorkstationStateTest {
    @Test void nativeWorkstationConstructsAndEveryPublishedStateIsValid() throws Exception {
        InventoryTestBootstrap.initialize();
        var block = new ECOIntegratedWorkingStation(BlockBehaviour.Properties.of());
        var definition = block.getStateDefinition();
        for (var property : definition.getProperties())
            assertTrue(property.getPossibleValues().size() > 1, property.getName());
        var file = Path.of("src/generated/resources/assets/neoecoae/blockstates/integrated_working_station.json");
        var variants = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("variants");
        assertEquals(definition.getPossibleStates().size(), variants.size());
        for (String variant : variants.keySet()) for (String field : variant.split(",")) {
            var pair = field.split("=");
            var property = definition.getProperty(pair[0]); assertNotNull(property, variant);
            assertTrue(property.getValue(pair[1]).isPresent(), variant);
        }
    }
}
