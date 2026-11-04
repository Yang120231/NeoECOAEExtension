package cn.dancingsnow.neoecoae.client.rendering;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Native GTO-edition models must retain their complete local parent and texture graph. */
class CellModelResourceTest {
    private static final List<Path> ROOTS = List.of(Path.of("src/main/resources/assets/neoecoae"),
        Path.of("src/generated/resources/assets/neoecoae"));

    @Test void localParentsTexturesAndOverridesRemainComplete() throws Exception {
        var models = models();
        assertFalse(models.isEmpty());
        for (var entry : models.entrySet()) {
            String name = entry.getKey();
            var model = entry.getValue();
            if (model.has("parent")) require(model.get("parent").getAsString(), "models", ".json", name);
            boolean cell = name.startsWith("block/cell/storage_cell_") || name.startsWith("item/cell/");
            var textures = cell ? textures(name, models, new HashSet<>())
                : model.has("textures") ? model.getAsJsonObject("textures") : new JsonObject();
            for (var texture : textures.entrySet()) {
                String value = texture.getValue().getAsString();
                require(cell ? resolve(value, textures) : value, "textures", ".png", name);
            }
            if (model.has("overrides")) for (var override : model.getAsJsonArray("overrides"))
                require(override.getAsJsonObject().get("model").getAsString(), "models", ".json", name);
        }
    }

    @Test void registeredNativeModelsAndItemCallbacksResolve() throws Exception {
        var literal = Pattern.compile("\"((?:block/cell|item/cell)/[a-z0-9_./]+)\"");
        var imported = Pattern.compile("importedCellModel\\(\"([a-z0-9_]+)\"\\)");
        var cell = Pattern.compile("ItemModelUtil\\.cellModel\\(\"([a-z0-9_]+)\",\\s*\"([a-z0-9]+)\"\\)");
        try (var files = Files.walk(Path.of("src/main/java/cn/dancingsnow/neoecoae"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                var matcher = literal.matcher(source);
                while (matcher.find()) require("neoecoae:" + matcher.group(1), "models", ".json", file.toString());
                matcher = imported.matcher(source);
                while (matcher.find()) require("neoecoae:item/cell/" + matcher.group(1), "models", ".json", file.toString());
                matcher = cell.matcher(source);
                while (matcher.find()) require("neoecoae:item/cell/eco_" + matcher.group(1) + "_cell_" + matcher.group(2),
                    "models", ".json", file.toString());
            }
        }
    }

    @Test void nativeCellStatusLightKeepsItsTintLayer() throws Exception {
        var models = models();
        for (String family : List.of("item", "fluid", "bulk_item")) {
            for (String size : List.of("16m", "64m", "256m")) {
                String name = "item/cell/eco_" + family + "_cell_" + size;
                var layers = textures(name, models, new HashSet<>());
                assertEquals("neoecoae:item/eco_cells/cell_status_light", layers.get("layer2").getAsString(), name);
            }
        }
    }

    @Test void allPublishedBlockStatesReferenceExistingLocalModels() throws Exception {
        var builtins = registeredBuiltinModels();
        for (var root : ROOTS) try (var files = Files.walk(root.resolve("blockstates"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList())
                blockModels(JsonParser.parseString(Files.readString(file)), file.toString(), builtins);
        }
    }

    private static Set<String> registeredBuiltinModels() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/cn/dancingsnow/neoecoae/client/all/NEBuiltinModels.java"));
        var registration = Pattern.compile(
            "register\\s*\\(\\s*NeoECOAE\\.id\\(\"([^\"]+)\"\\)\\s*,\\s*[\\w$.]+::new\\s*\\)");
        var models = new HashSet<String>();
        var matcher = registration.matcher(source);
        while (matcher.find()) models.add("neoecoae:" + matcher.group(1));
        assertFalse(models.isEmpty(), "No registered builtin model factories found");
        return models;
    }

    private static void blockModels(com.google.gson.JsonElement value, String owner, Set<String> builtins) {
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
            if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) {
                String model = entry.getValue().getAsString();
                if (!builtins.contains(model)) require(model, "models", ".json", owner);
            } else blockModels(entry.getValue(), owner, builtins);
        }
        else if (value.isJsonArray()) for (var child : value.getAsJsonArray()) blockModels(child, owner, builtins);
    }

    @Test void remainingAnimationMetadataHasValidImagesAndFrames() throws Exception {
        for (var root : ROOTS) {
            try (var files = Files.walk(root)) {
                for (var metadata : files.filter(path -> path.toString().endsWith(".png.mcmeta")).toList()) {
                    var image = ImageIO.read(metadata.resolveSibling(metadata.getFileName().toString().replace(".mcmeta", "")).toFile());
                    assertNotNull(image, metadata.toString());
                    var json = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
                    if (!json.has("animation")) continue;
                    var animation = json.getAsJsonObject("animation");
                    int width = animation.has("width") ? animation.get("width").getAsInt() : image.getWidth();
                    int height = animation.has("height") ? animation.get("height").getAsInt() : width;
                    assertTrue(width > 0 && height > 0 && image.getWidth() % width == 0 && image.getHeight() % height == 0,
                        metadata.toString());
                    int count = image.getWidth() / width * (image.getHeight() / height);
                    if (animation.has("frames")) for (var frame : animation.getAsJsonArray("frames")) {
                        int index = frame.isJsonObject() ? frame.getAsJsonObject().get("index").getAsInt() : frame.getAsInt();
                        assertTrue(index >= 0 && index < count, metadata + " frame " + index);
                    }
                }
            }
        }
    }

    private static Map<String, JsonObject> models() throws Exception {
        Map<String, JsonObject> models = new LinkedHashMap<>();
        for (var root : ROOTS) try (var files = Files.walk(root.resolve("models"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList())
                models.put(root.resolve("models").relativize(file).toString().replace('\\', '/').replace(".json", ""),
                    JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        }
        return models;
    }
    private static JsonObject textures(String name, Map<String, JsonObject> models, Set<String> visited) {
        assertTrue(visited.add(name), "Parent cycle: " + name);
        var model = models.get(name); assertNotNull(model, name);
        var result = new JsonObject();
        if (model.has("parent") && model.get("parent").getAsString().startsWith("neoecoae:"))
            result = textures(model.get("parent").getAsString().substring("neoecoae:".length()), models, visited);
        if (model.has("textures")) for (var entry : model.getAsJsonObject("textures").entrySet())
            result.add(entry.getKey(), entry.getValue());
        return result;
    }
    private static String resolve(String value, JsonObject textures) {
        var seen = new HashSet<String>();
        while (value.startsWith("#")) {
            assertTrue(seen.add(value), "Texture variable cycle: " + value);
            var next = textures.get(value.substring(1)); assertNotNull(next, value); value = next.getAsString();
        }
        return value;
    }
    private static void require(String id, String folder, String extension, String owner) {
        if (id.startsWith("neoecoae:")) assertTrue(ROOTS.stream().anyMatch(root ->
            Files.isRegularFile(root.resolve(folder + "/" + id.substring("neoecoae:".length()) + extension))),
            owner + " references missing " + id);
    }
}
