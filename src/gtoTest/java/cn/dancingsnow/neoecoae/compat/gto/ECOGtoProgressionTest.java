package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** GTO keeps ECO's workstations: the GTO material plan is processed by the integrated workstations. */
class ECOGtoProgressionTest {
    private static final Path PACK = Path.of("src/main/resources/recipe_profiles/gt_gto/data/neoecoae/recipes");
    private static final long AE_PER_EU = 8;

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Path recipe(String id) {
        return PACK.resolve(id.substring(id.indexOf(':') + 1) + ".json");
    }

    private static Map<Path, JsonObject> pack() throws Exception {
        var result = new HashMap<Path, JsonObject>();
        try (var files = Files.walk(PACK)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) result.put(file, read(file));
        }
        return result;
    }

    private static boolean workstation(JsonObject recipe) {
        String type = recipe.get("type").getAsString();
        return type.equals("neoecoae:integrated_working_station") || type.equals("neoecoae:large_integrated_working_station");
    }

    @Test void everyPlannedProductIsOneWorkstationRecipeWithThePlannedMaterialsAndGtDuration() throws Exception {
        var plan = read(Path.of("scripts/gto-workstation-plan.json")).getAsJsonArray("recipes");
        assertEquals(26, plan.size());
        int converted = 0;
        for (var value : plan) {
            var row = value.getAsJsonObject();
            var actual = read(recipe(row.get("replaces").getAsString()));
            if (row.has("copy_from")) {
                assertEquals("forge:conditional", actual.get("type").getAsString(), "Exchange-only route: " + row);
                assertTrue(actual.getAsJsonArray("recipes").isEmpty());
                continue;
            }
            converted++;
            var inputs = row.getAsJsonArray("item_inputs");
            assertEquals(inputs.size() > 9 ? "neoecoae:large_integrated_working_station" : "neoecoae:integrated_working_station",
                actual.get("type").getAsString(), row.get("id").getAsString());
            assertEquals(inputs, actual.getAsJsonArray("inputItems"));
            assertEquals(row.get("ticks").getAsInt(), actual.get("processingTicks").getAsInt());
            assertEquals(row.get("eu_per_tick").getAsLong() * row.get("ticks").getAsLong() * AE_PER_EU,
                actual.get("energy").getAsLong());
            for (var field : List.of(List.of("input_fluid", "inputFluid"), List.of("item_output", "itemOutput"),
                    List.of("fluid_output", "fluidOutput"))) {
                assertEquals(row.get(field.get(0)), actual.get(field.get(1)), row.get("id").getAsString());
            }
        }
        assertEquals(25, converted);
    }

    @Test void largeOnlyRecipesFitTheLargeWorkstationAndRegularOnesFitTheRegularOne() throws Exception {
        boolean sawLarge = false;
        for (var recipe : pack().values()) {
            if (!workstation(recipe)) continue;
            int inputs = recipe.getAsJsonArray("inputItems").size();
            boolean large = recipe.get("type").getAsString().equals("neoecoae:large_integrated_working_station");
            sawLarge |= large;
            assertTrue(inputs > 0 && inputs <= (large ? 12 : 9), recipe.toString());
            assertTrue(recipe.get("energy").getAsLong() > 0 && recipe.get("energy").getAsLong() <= Integer.MAX_VALUE);
        }
        assertTrue(sawLarge, "The over-sized network switch is large-workstation only");
        assertEquals("neoecoae:large_integrated_working_station",
            read(recipe("neoecoae:integrated_working_station/crafting_high_energy_network_switch")).get("type").getAsString());
    }

    @Test void namedTiersKeepTheApprovedVoltageAndMatchingGtCircuits() throws Exception {
        for (var value : read(Path.of("scripts/gto-workstation-plan.json")).getAsJsonArray("recipes")) {
            var row = value.getAsJsonObject();
            if (!row.has("item_output") || row.has("copy_from")) continue;
            String output = row.getAsJsonObject("item_output").get("item").getAsString();
            int level = output.endsWith("_l4") || output.endsWith("_16m") ? 4
                : output.endsWith("_l6") || output.endsWith("_64m") ? 6
                : output.endsWith("_l9") || output.endsWith("_256m") || output.endsWith("eco_infinite_item_cell") ? 9 : 0;
            if (level == 0) continue;
            String tier = switch (level) { case 4 -> "ev"; case 6 -> "iv"; default -> "luv"; };
            assertEquals(switch (level) { case 4 -> 1920; case 6 -> 7680; default -> 30720; }, row.get("eu_per_tick").getAsInt(), output);
            assertTrue(row.getAsJsonArray("item_inputs").asList().stream().anyMatch(input ->
                input.getAsJsonObject().has("tag") && input.getAsJsonObject().get("tag").getAsString()
                    .equals("gtceu:circuits/" + tier)), output);
        }
    }

    @Test void theWorkstationItselfKeepsTheStandaloneBootstrapChain() throws Exception {
        assertEquals("minecraft:crafting_shaped", read(recipe("neoecoae:integrated_working_station")).get("type").getAsString());
        assertEquals("minecraft:crafting_shaped", read(recipe("neoecoae:aluminum_alloy_casing")).get("type").getAsString());
        for (String id : List.of("neoecoae:aluminum_alloy_dust", "neoecoae:aluminum_alloy_ingot_from_dusts_aluminum_alloy_smelting",
                "neoecoae:aluminum_alloy_ingot_from_dusts_aluminum_alloy_blasting", "neoecoae:transform/crystal_ingot",
                "neoecoae:transform/energized_fluix_crystal", "neoecoae:transform/energized_superconductive_ingot")) {
            assertNotEquals("forge:conditional", read(recipe(id)).get("type").getAsString(), id);
        }
        // B16 keeps the empty module makeable before a large workstation exists.
        assertEquals("neoecoae:integrated_working_station", read(recipe(
            "neoecoae:large_integrated_working_station/integrated_working_station_module_empty")).get("type").getAsString());
    }

    @Test void workstationsModulesAndLargePartsAreNoLongerRemovedAndNoOmniOrMegaRouteIsAdded() throws Exception {
        for (var entry : pack().entrySet()) {
            var recipe = entry.getValue();
            String path = PACK.relativize(entry.getKey()).toString().replace('\\', '/');
            if (recipe.get("type").getAsString().equals("forge:conditional")) {
                boolean omniOrMega = path.contains("omni_cell") || path.contains("spent_nuclear_waste_cell")
                    || path.startsWith("compat/megacells/");
                assertFalse(!omniOrMega && (path.contains("integrated_working_station") || path.startsWith("transform/")), path);
                continue;
            }
            assertFalse(recipe.toString().contains("ae2omnicells"), path);
            assertFalse(recipe.toString().contains("megacells"), path);
        }
        for (String id : List.of("neoecoae:integrated_working_station/module_extendedae", "neoecoae:large_integrated_working_station_casing",
                "neoecoae:large_integrated_working_station_interface",
                "neoecoae:large_integrated_working_station/integrated_working_station_module_extendedae")) {
            // Either replaced in the GTO pack or left to the base mod's own recipe; never emptied.
            Path file = Files.exists(recipe(id)) ? recipe(id)
                : Path.of("src/main/resources/data/neoecoae/recipes", id.substring(id.indexOf(':') + 1) + ".json");
            assertTrue(workstation(read(file)), id);
        }
    }

    @Test void chargerReplacesLightningForTheFirstEnergizedCrystalWithTheSameBuddingMapping() throws Exception {
        var mapping = Map.of("flawless", "flawless", "flawed", "flawed", "chipped", "chipped", "damaged", "chipped");
        for (var entry : mapping.entrySet()) {
            var charger = read(recipe("neoecoae:compat/gto/charger/" + entry.getKey() + "_budding_quartz"));
            assertEquals("ae2:charger", charger.get("type").getAsString());
            assertEquals("ae2:" + entry.getKey() + "_budding_quartz", charger.getAsJsonObject("ingredient").get("item").getAsString());
            assertEquals("neoecoae:" + entry.getValue() + "_budding_energized_crystal",
                charger.getAsJsonObject("result").get("item").getAsString());
        }
    }

    @Test void gtoLoadsOneSelfContainedPackSoNoProfileOrderCanUndoIt() throws Exception {
        assertEquals(List.of("gt_gto"), cn.dancingsnow.neoecoae.recipe.profile.GTRecipeProfiles.packPaths(
            cn.dancingsnow.neoecoae.config.RecipeProfileConfig.Profile.AUTO, mod -> !mod.equals("gtlcore"),
            () -> { throw new AssertionError("GTO does not use a GT machine JSON schema"); }));
        // Everything the GTM profile ships is present beneath the GTO replacements.
        Path gtm = Path.of("src/main/resources/recipe_profiles/gtm/data/neoecoae/recipes");
        try (var files = Files.walk(gtm)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                assertTrue(Files.exists(PACK.resolve(gtm.relativize(file).toString())), file.toString());
            }
        }
    }
}
