package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ECOGtoProgressionTest {
    private static final Path PACK = Path.of("src/main/resources/recipe_profiles/gt_gto");

    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Map<String, JsonObject> nativeRecipes() throws Exception {
        var result = new HashMap<String, JsonObject>();
        for (var value : read(PACK.resolve("native_recipes.json")).getAsJsonArray("recipes")) {
            var row = value.getAsJsonObject();
            assertNull(result.put(row.get("id").getAsString(), row), "Native recipe IDs must be unique");
        }
        return result;
    }

    @Test void gtoOverridesRemoveEveryWorkstationRecipeAndItsMachineAndCards() throws Exception {
        var recipes = new HashMap<String, JsonObject>();
        for (String root : new String[] {"src/generated/resources", "src/main/resources",
                "src/main/resources/recipe_profiles/gtm", "src/main/resources/recipe_profiles/gt_gto"}) {
            Path base = Path.of(root, "data/neoecoae/recipes");
            try (var files = Files.walk(base)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                    recipes.put(base.relativize(file).toString(), read(file));
                }
            }
        }
        assertFalse(recipes.isEmpty());
        recipes.forEach((id, row) -> assertFalse(ECOGtoWorkstations.retiredRecipe(row), id));
        // A surviving product recipe must not require a retired machine or its module either.
        for (var row : nativeRecipes().values()) {
            for (var value : row.has("item_inputs") ? row.getAsJsonArray("item_inputs") : new com.google.gson.JsonArray()) {
                var input = value.getAsJsonObject();
                if (input.has("item")) assertFalse(ECOGtoWorkstations.retiredItem(
                    new ResourceLocation(input.get("item").getAsString())), row.toString());
            }
            if (row.has("item_output")) assertFalse(ECOGtoWorkstations.retiredItem(new ResourceLocation(
                row.getAsJsonObject("item_output").get("item").getAsString())), row.toString());
        }
    }

    @Test void allTwentySixProductsHaveExactlyOneNativeRouteAndNoOmniOrMegaDependencies() throws Exception {
        var recipes = nativeRecipes();
        var products = new HashSet<String>();
        var progression = read(Path.of("scripts/gto-workstation-plan.json")).getAsJsonArray("recipes");
        assertEquals(26, progression.size());
        for (var value : progression) {
            var expected = value.getAsJsonObject();
            var actual = recipes.get(expected.get("id").getAsString());
            assertNotNull(actual);
            String output = expected.has("item_output") ? expected.getAsJsonObject("item_output").get("item").getAsString()
                : expected.getAsJsonObject("fluid_output").get("fluid").getAsString();
            assertTrue(products.add(output), "Each migrated product has one route: " + output);
            assertEquals(1, recipes.values().stream().filter(row ->
                row.has("item_output") && row.getAsJsonObject("item_output").get("item").getAsString().equals(output)
                    || row.has("fluid_output") && row.getAsJsonObject("fluid_output").get("fluid").getAsString().equals(output)).count());
        }
        for (var row : recipes.values()) {
            assertFalse(row.toString().contains("ae2omnicells"));
            assertFalse(row.toString().contains("megacells"));
        }
    }

    @Test void namedTiersUseTheApprovedVoltageAndMatchingGtCircuitsAndParts() throws Exception {
        for (var row : nativeRecipes().values()) {
            if (!row.has("item_output") || row.has("copy_from")) continue;
            String output = row.getAsJsonObject("item_output").get("item").getAsString();
            int level = output.endsWith("_l4") || output.endsWith("_16m") ? 4
                : output.endsWith("_l6") || output.endsWith("_64m") ? 6
                : output.endsWith("_l9") || output.endsWith("_256m") || output.endsWith("eco_infinite_item_cell") ? 9 : 0;
            if (level == 0) continue;
            String tier = switch (level) { case 4 -> "ev"; case 6 -> "iv"; default -> "luv"; };
            assertEquals(switch (level) { case 4 -> 1920; case 6 -> 7680; default -> 30720; },
                row.get("eu_per_tick").getAsInt(), output);
            assertTrue(row.getAsJsonArray("item_inputs").asList().stream().anyMatch(input ->
                input.getAsJsonObject().has("tag") && input.getAsJsonObject().get("tag").getAsString()
                    .equals("gtceu:circuits/" + tier)), output);
            assertFalse(row.toString().matches(".*gtceu:(?:zpm|uv|uhv|uev)_.*"), output);
        }
    }

    @Test void inputsFitActualGtoMachineSlotsAndCoolantIsAFluidOnlyOutput() throws Exception {
        Map<String, Integer> slots = Map.of("gtceu:assembler", 9, "gtceu:assembly_line", 16,
            "gtceu:circuit_assembler", 6, "gtceu:mixer", 6, "gtceu:autoclave", 2,
            "gtceu:alloy_smelter", 2, "gtceu:electric_blast_furnace", 3, "gtceu:macerator", 1);
        for (var row : nativeRecipes().values()) {
            if (row.has("copy_from")) continue;
            String type = row.get("recipe_type").getAsString();
            assertTrue(row.getAsJsonArray("item_inputs").size() <= slots.get(type), row.toString());
            assertTrue(row.get("eu_per_tick").getAsLong() > 0);
            assertTrue(row.get("ticks").getAsInt() > 0);
        }
        var coolant = nativeRecipes().get("neoecoae:compat/gto/cryotheum_solution");
        assertEquals("gtceu:mixer", coolant.get("recipe_type").getAsString());
        assertFalse(coolant.has("item_output"));
        assertEquals("neoecoae:cryotheum_solution", coolant.getAsJsonObject("fluid_output").get("fluid").getAsString());
        assertEquals(1000, coolant.getAsJsonObject("fluid_output").get("amount").getAsInt());
    }

    @Test void retiredIdentitiesAreScopedAndOldJsonIsRejectedBeforeOptionalIngredientsAreResolved() {
        for (String path : Set.of("integrated_working_station", "large_integrated_working_station_casing",
                "large_integrated_working_station_input_hatch", "large_integrated_working_station_output_hatch",
                "large_integrated_working_station_interface", "integrated_working_station_module_empty",
                "integrated_working_station_module_extendedae")) {
            assertTrue(ECOGtoWorkstations.retiredItem(new ResourceLocation("neoecoae", path)));
            assertFalse(ECOGtoWorkstations.retiredItem(new ResourceLocation("gtocore", path)));
        }
        assertFalse(ECOGtoWorkstations.retiredItem(new ResourceLocation("neoecoae:storage_system_l4")));
        var old = JsonParser.parseString("{\"type\":\"neoecoae:integrated_working_station\","
            + "\"inputItems\":[{\"item\":\"missing_addon:component\",\"count\":1}]}");
        assertTrue(ECOGtoRecipes.decode(Map.of(new ResourceLocation("neoecoae:legacy"), old), null).isEmpty());
    }
}
