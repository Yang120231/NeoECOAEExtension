package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cn.dancingsnow.neoecoae.integration.emi.ECOGtoEmiRecipes;
import cn.dancingsnow.neoecoae.integration.emi.NeoECOAEEmiPlugin;
import com.google.gson.JsonParser;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ECOGtoRecipeVisibilityTest {
    private static EmiRecipe recipe(String id) {
        var result = mock(EmiRecipe.class);
        when(result.getId()).thenReturn(new ResourceLocation(id));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Test void lateCasingsArePublishedEvenWhenGtosEarlierCatalogueHasNoneAndReloadDoesNotDuplicate() throws Exception {
        var other = recipe("gtceu:assembler/titanium_machine_casing");
        var black = recipe("neoecoae:assembler/compat/gt/bootstrap/b12_black_tungsten_alloy_casing");
        var aluminium = recipe("neoecoae:assembler/compat/gt/bootstrap/b11_aluminum_alloy_casing");
        var cached = new ArrayList<>(List.of(other));
        var invalidators = new ArrayList<Predicate<EmiRecipe>>();
        var registry = mock(EmiRegistry.class);
        doAnswer(call -> {
            invalidators.add((Predicate<EmiRecipe>) call.getArgument(0));
            return null;
        }).when(registry).removeRecipes(any(Predicate.class));
        doAnswer(call -> { cached.add(call.getArgument(0)); return null; }).when(registry).addRecipe(any());
        assertTrue(cached.stream().noneMatch(value -> value.getId().getNamespace().equals("neoecoae")),
            "This is the old failure: the prebuilt GTO catalogue has no late ECO recipes");
        var publish = ECOGtoEmiRecipes.class.getDeclaredMethod("replaceDisplays", EmiRegistry.class, Map.class);
        publish.setAccessible(true);
        var late = Map.of(black.getId(), black, aluminium.getId(), aluminium);
        publish.invoke(null, registry, late);
        invalidators.forEach(cached::removeIf);
        invalidators.clear();
        assertEquals(3, cached.size());
        assertTrue(cached.containsAll(List.of(other, black, aluminium)));
        var nextBlack = recipe(black.getId().toString());
        var nextAluminium = recipe(aluminium.getId().toString());
        publish.invoke(null, registry, Map.of(nextBlack.getId(), nextBlack, nextAluminium.getId(), nextAluminium));
        invalidators.forEach(cached::removeIf);
        assertEquals(3, cached.size());
        assertTrue(cached.containsAll(List.of(nextBlack, nextAluminium)));
        assertSame(other, cached.get(0), "Unrelated GTO recipes must survive");
    }

    @Test void nativeDefinitionLookupUsesTheSavedTypePrefixForDeduplicationAndResearchSource() throws Exception {
        var id = new ResourceLocation("neoecoae:compat/gt/bootstrap/b12_black_tungsten_alloy_casing");
        var savedId = new ResourceLocation("neoecoae:assembler/compat/gt/bootstrap/b12_black_tungsten_alloy_casing");
        Object existing = new Object();
        var definitions = Map.of(savedId, existing);
        assertNull(definitions.get(id), "Looking up the bare builder ID used to miss every saved definition");
        assertSame(existing, definitions.get(ECOGtoNativeRecipes.registeredId(id, new ResourceLocation("gtceu:assembler"))));
        var data = JsonParser.parseString(Files.readString(Path.of("src/main/resources/recipe_profiles/gt_gto/native_recipes.json")))
            .getAsJsonObject().getAsJsonArray("recipes");
        String source = data.asList().stream().map(value -> value.getAsJsonObject()).filter(value -> value.has("copy_from"))
            .findFirst().orElseThrow().get("copy_from").getAsString();
        assertEquals("gtceu:assembly_line/infinite_cell_component", source);
    }

    @Test void allNativeRecipesIncludingCasingsArePartOfThePublishedCatalogue() throws Exception {
        var rows = JsonParser.parseString(Files.readString(Path.of("src/main/resources/recipe_profiles/gt_gto/native_recipes.json")))
            .getAsJsonObject().getAsJsonArray("recipes");
        var ids = new HashMap<ResourceLocation, String>();
        for (var raw : rows) {
            var row = raw.getAsJsonObject();
            String type = row.has("copy_from") ? "gtceu:assembly_line" : row.get("recipe_type").getAsString();
            var id = ECOGtoNativeRecipes.registeredId(new ResourceLocation(row.get("id").getAsString()), new ResourceLocation(type));
            assertNull(ids.put(id, row.has("item_output") ? row.getAsJsonObject("item_output").get("item").getAsString()
                : row.getAsJsonObject("fluid_output").get("fluid").getAsString()));
        }
        assertEquals(40, ids.size());
        for (String item : List.of("aluminum_alloy_casing", "black_tungsten_alloy_casing", "crafting_casing",
                "storage_casing", "computation_casing")) {
            assertTrue(ids.containsValue("neoecoae:" + item), item);
        }
    }

    @Test void ecoPluginActuallyPublishesLateGtoDisplays() throws Exception {
        try (var input = NeoECOAEEmiPlugin.class.getResourceAsStream("NeoECOAEEmiPlugin.class")) {
            var plugin = new ClassNode();
            new ClassReader(input).accept(plugin, 0);
            assertTrue(plugin.methods.stream().filter(method -> method.name.equals("register"))
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .anyMatch(instruction -> instruction instanceof MethodInsnNode call
                    && call.owner.equals("cn/dancingsnow/neoecoae/integration/emi/ECOGtoEmiRecipes")
                    && call.name.equals("register")));
        }
    }

    @Test void installedGtoKeepsTheCachedCatalogueAndTheNativeDisplayAndSavedIdContracts() throws Exception {
        String path = System.getenv("NEOECOAE_GTO_CORE_JAR");
        org.junit.jupiter.api.Assumptions.assumeTrue(path != null && !path.isBlank());
        Path core = Path.of(path);
        Path gt;
        try (var files = Files.list(core.getParent())) {
            gt = files.filter(file -> file.getFileName().toString().startsWith("gtceu-") && file.toString().endsWith(".jar"))
                .findFirst().orElseThrow();
        }
        var display = node(core, "com/gtocore/integration/emi/GTEMIRecipe");
        assertTrue(display.methods.stream().anyMatch(method -> method.name.equals("<init>") && method.desc.equals(
            "(Lcom/gregtechceu/gtceu/api/recipe/GTRecipeDefinition;Ldev/emi/emi/api/recipe/EmiRecipeCategory;)V")));
        var cache = node(core, "com/gtocore/common/data/GTORecipes");
        assertTrue(cache.fields.stream().anyMatch(field -> field.name.equals("EMI_RECIPES")));
        Path emi;
        try (var files = Files.list(core.getParent())) {
            emi = files.filter(file -> file.getFileName().toString().startsWith("emi-") && file.toString().endsWith(".jar"))
                .findFirst().orElseThrow();
        }
        var emiRegistry = node(emi, "dev/emi/emi/registry/EmiRegistryImpl");
        var remove = emiRegistry.methods.stream().filter(method -> method.name.equals("removeRecipes")).findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(remove.instructions.toArray()).anyMatch(instruction -> instruction instanceof FieldInsnNode field
            && field.name.equals("invalidators")), "The instance queues predicates until after recipe registration");
        var data = node(core, "com/gtocore/data/Data");
        assertTrue(data.methods.stream().flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
            .anyMatch(instruction -> instruction instanceof FieldInsnNode field && field.name.equals("EMI_RECIPES")),
            "The target instance prebuilds its GT recipe catalogue");
        var builder = node(gt, "com/gregtechceu/gtceu/api/recipe/GTRecipeBuilder");
        var build = builder.methods.stream().filter(method -> method.name.equals("build") && method.desc.startsWith("(Z)"))
            .findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(build.instructions.toArray()).anyMatch(instruction -> instruction instanceof FieldInsnNode field
            && field.owner.equals("com/gregtechceu/gtceu/api/recipe/GTRecipeType") && field.name.equals("registryName")));
        assertTrue(java.util.Arrays.stream(build.instructions.toArray()).anyMatch(instruction -> instruction instanceof MethodInsnNode call
            && call.owner.equals("net/minecraft/resources/ResourceLocation")
            && call.desc.equals("(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")),
            "The saved definition ID prepends its recipe type");
    }

    private static ClassNode node(Path jarPath, String name) throws Exception {
        try (var jar = new JarFile(jarPath.toFile()); var input = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            var result = new ClassNode();
            new ClassReader(input).accept(result, 0);
            return result;
        }
    }
}
