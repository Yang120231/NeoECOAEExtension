package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** In GTO the ECO infinite component is only exchanged 1:1 with GTO's own component on the crafting table. */
class ECOGtoInfiniteComponentsTest {
    private static final Path ROOT = Path.of("src/main/resources/recipe_profiles/gt_gto");
    private static final String ECO = "neoecoae:eco_infinite_cell_component";
    private static final String GTO = "gtocore:infinite_cell_component";

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String output(JsonObject recipe) {
        for (String key : List.of("result", "itemOutput")) {
            JsonElement value = recipe.get(key);
            if (value != null && value.isJsonObject() && value.getAsJsonObject().has("item")) {
                return value.getAsJsonObject().get("item").getAsString();
            }
        }
        return "";
    }

    @Test void gtoPackOnlyExchangesTheComponentsAndHasNoOtherRoute() throws Exception {
        var removed = read(ROOT.resolve("data/neoecoae/recipes/eco_infinite_cell_component.json"));
        assertEquals("forge:conditional", removed.get("type").getAsString());
        assertTrue(removed.getAsJsonArray("recipes").isEmpty());
        for (String direction : List.of("eco", "gto")) {
            var conversion = read(ROOT.resolve("data/neoecoae/recipes/compat/gto/infinite_component_to_" + direction + ".json"));
            assertEquals("minecraft:crafting_shapeless", conversion.get("type").getAsString());
            assertEquals(1, conversion.getAsJsonArray("ingredients").size());
            assertEquals(1, conversion.getAsJsonObject("result").get("count").getAsInt());
            String input = conversion.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString();
            assertEquals(Set.of(ECO, GTO), Set.of(input, output(conversion)));
        }
        try (var files = Files.walk(ROOT.resolve("data"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String relative = ROOT.relativize(file).toString().replace('\\', '/');
                if (relative.contains("infinite_component_to_")) continue;
                // No other route, and no shared tag that would let ECO storage accept GTO's component.
                assertFalse(Files.readString(file).contains(GTO), relative);
                if (relative.contains("/recipes/")) assertNotEquals(ECO, output(read(file)), relative);
            }
        }
    }
}
