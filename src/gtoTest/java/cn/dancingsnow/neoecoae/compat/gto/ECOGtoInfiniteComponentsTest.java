package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

class ECOGtoInfiniteComponentsTest {
    private static Item eco;
    private static Item gto;

    @BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        eco = item("neoecoae:eco_infinite_cell_component");
        gto = item("gtocore:infinite_cell_component");
    }

    private static Item item(String name) {
        var id = new ResourceLocation(name);
        if (!ForgeRegistries.ITEMS.containsKey(id)) ForgeRegistries.ITEMS.register(id, new Item(new Item.Properties()));
        return ForgeRegistries.ITEMS.getValue(id);
    }

    @Test void nativeStorageAcceptsEitherComponentWithoutOpeningUnrelatedSlots() {
        Predicate<ItemStack> nativeOnly = stack -> stack.is(gto) && stack.getCount() <= 64;
        var filter = ECOGtoInfiniteComponents.storageFilter(nativeOnly);
        assertTrue(filter.test(new ItemStack(gto, 64)));
        var withTag = new ItemStack(eco, 64);
        withTag.getOrCreateTag().putString("custom", "preserve");
        assertTrue(filter.test(withTag));
        assertFalse(filter.test(new ItemStack(eco, 65)));
        assertFalse(filter.test(new ItemStack(Items.DIAMOND, 64)));
        assertFalse(filter.test(ItemStack.EMPTY));
        assertFalse(ECOGtoInfiniteComponents.storageFilter(stack -> stack.is(Items.DIAMOND))
                .test(new ItemStack(eco)));
        assertFalse(ECOGtoInfiniteComponents.storageFilter(stack -> stack.is(gto) && !stack.hasTag()).test(withTag));
        assertNull(ECOGtoInfiniteComponents.storageFilter(null));
    }

    @Test void gtRecipeInputsUseOneSharedTagAndKeepTheRequiredCount() {
        var builder = new Builder(new ResourceLocation("test:recipe"), new Type());
        assertSame(builder, ECOGtoInfiniteComponents.addRecipeInput(builder, new ItemStack(eco, 64)));
        assertEquals("neoecoae:infinite_cell_components", builder.tag.location().toString());
        assertEquals(64, builder.count);
        assertTrue(ECOGtoInfiniteComponents.isComponent(new ItemStack(gto)));
        assertFalse(ECOGtoInfiniteComponents.isComponent(new ItemStack(Items.DIAMOND)));
    }

    @Test void copiedAssemblyLineKeepsAllInputsResearchAndPowerAndDoesNotShareMutableData() throws Exception {
        var source = new Definition();
        var id = new ResourceLocation("neoecoae:compat/gto/eco_infinite_cell_component");
        var copy = (Builder) ECOGtoRecipeCopies.withOutput(source, id, Builder.class);
        assertEquals(id, copy.id);
        assertEquals(7, copy.itemInputs.entries.size());
        assertEquals(List.of(20_000L, 20_000L, 100_000L), copy.fluidInputs.entries);
        assertEquals(List.of(source.conditions), copy.conditions);
        assertEquals("existing-256m-research", copy.conditions.get(0));
        assertEquals(1_966_080L, copy.eut);
        assertEquals(2400, copy.duration);
        assertEquals(4_718_592_000L, copy.eut * copy.duration);
        assertTrue(copy.itemOutputs.entries.isEmpty(), "Only the new output is added by the registration caller");
        assertNotSame(source.data, copy.data);
        assertSame(source.recipeCategory, copy.recipeCategory);
        assertSame(source.chanceFunction, copy.chanceFunction);
        assertEquals(List.of(source.recipeExtensions), copy.extensions);
        assertEquals(List.of(source.tickRecipeExtensions), copy.tickExtensions);
        assertEquals(List.of(source.recipeModifiers), copy.modifiers);
        copy.itemInputs.entries.clear();
        assertEquals(7, source.itemInputs.entries.size(), "The original GTO recipe stays immutable");
    }

    @Test void gtoPackRemovesTheCheapRouteAndProvidesBothLosslessConversions() throws Exception {
        var root = Path.of("src/main/resources/recipe_profiles/gt_gto");
        var removed = read(root.resolve("data/neoecoae/recipes/eco_infinite_cell_component.json"));
        assertEquals("forge:conditional", removed.get("type").getAsString());
        assertTrue(removed.getAsJsonArray("recipes").isEmpty());
        var nativeRows = read(root.resolve("native_recipes.json")).getAsJsonArray("recipes");
        var copy = nativeRows.asList().stream().map(value -> value.getAsJsonObject())
                .filter(value -> value.has("copy_from")).findFirst().orElseThrow();
        assertEquals("gtceu:assembly_line/infinite_cell_component", copy.get("copy_from").getAsString());
        assertEquals("neoecoae:eco_infinite_cell_component", copy.getAsJsonObject("item_output").get("item").getAsString());
        for (String direction : List.of("eco", "gto")) {
            var conversion = read(root.resolve("data/neoecoae/recipes/compat/gto/infinite_component_to_" + direction + ".json"));
            assertEquals(1, conversion.getAsJsonArray("ingredients").size());
            assertEquals(1, conversion.getAsJsonObject("result").get("count").getAsInt());
            String input = conversion.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString();
            String output = conversion.getAsJsonObject("result").get("item").getAsString();
            assertEquals(Set.of("neoecoae:eco_infinite_cell_component", "gtocore:infinite_cell_component"), Set.of(input, output));
        }
        var tag = read(root.resolve("data/neoecoae/tags/items/infinite_cell_components.json"));
        assertEquals(Set.of("neoecoae:eco_infinite_cell_component", "gtocore:infinite_cell_component"),
                Set.copyOf(tag.getAsJsonArray("values").asList().stream().map(value -> value.getAsString()).toList()));
    }

    @Test void installedGtoStillHasTheRequiredBuilderAndStorageHooks() throws Exception {
        String path = System.getenv("NEOECOAE_GTO_CORE_JAR");
        org.junit.jupiter.api.Assumptions.assumeTrue(path != null && !path.isBlank());
        var core = Path.of(path);
        Path gt;
        try (var files = Files.list(core.getParent())) {
            gt = files.filter(file -> file.getFileName().toString().startsWith("gtceu-") && file.toString().endsWith(".jar"))
                    .findFirst().orElseThrow();
        }
        var storage = node(gt, "com/gregtechceu/gtceu/api/transfer/key/StackInventory");
        assertTrue(storage.methods.stream().anyMatch(method -> method.name.equals("setFilter")
                && method.desc.equals("(Ljava/util/function/Predicate;)V")));
        var builder = node(gt, "com/gregtechceu/gtceu/api/recipe/GTRecipeBuilder");
        assertTrue(builder.methods.stream().anyMatch(method -> method.name.equals("inputItems")
                && method.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Lcom/gregtechceu/gtceu/api/recipe/GTRecipeBuilder;")));
        var definition = node(gt, "com/gregtechceu/gtceu/api/recipe/GTRecipeDefinition");
        for (String field : List.of("recipeCategory", "eut", "tier", "duration", "priority", "chanceFunction", "data")) {
            var target = builder.fields.stream().filter(value -> value.name.equals(field)).findFirst().orElseThrow();
            assertEquals(target.desc, definition.fields.stream().filter(value -> value.name.equals(field)).findFirst().orElseThrow().desc);
        }
    }

    private static ClassNode node(Path path, String name) throws Exception {
        try (var jar = new JarFile(path.toFile()); var input = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    public static class Type {}
    public static class Content {
        final List<Long> entries = new ArrayList<>();
        public Content addAll(Content source) { entries.addAll(source.entries); return this; }
    }
    public static class Data implements Cloneable {
        @Override public Data clone() { return new Data(); }
    }
    public static class Definition {
        public final Type recipeType = new Type();
        public final Object recipeCategory = new Object(), chanceFunction = new Object();
        public final Content itemInputs = new Content(), fluidInputs = new Content(), fluidOutputs = new Content();
        public final Object[] conditions = {"existing-256m-research"}, recipeExtensions = {"extension"},
                tickRecipeExtensions = {"tick-extension"}, recipeModifiers = {"modifier"};
        public final Data data = new Data();
        public final long eut = 1_966_080;
        public final int tier = 9, duration = 2400, priority = 0;
        Definition() {
            itemInputs.entries.addAll(List.of(64L, 16L, 16L, 64L, 64L, 64L, 4L));
            fluidInputs.entries.addAll(List.of(20_000L, 20_000L, 100_000L));
        }
    }
    public static class Builder {
        final ResourceLocation id;
        private Object recipeCategory, chanceFunction;
        private long eut;
        private int tier, duration, priority;
        private Data data;
        final Content itemInputs = new Content(), itemOutputs = new Content(), fluidInputs = new Content(), fluidOutputs = new Content();
        final List<Object> conditions = new ArrayList<>();
        final List<Object> extensions = new ArrayList<>(), tickExtensions = new ArrayList<>(), modifiers = new ArrayList<>();
        TagKey<Item> tag;
        int count;
        public Builder(ResourceLocation id, Type type) { this.id = id; }
        public Content getItemInputs() { return itemInputs; }
        public Content getFluidInputs() { return fluidInputs; }
        public Content getFluidOutputs() { return fluidOutputs; }
        public Builder addCondition(Collection<?> conditions) { this.conditions.addAll(conditions); return this; }
        public Builder addExtension(Object value) { extensions.add(value); return this; }
        public Builder addTickExtension(Object value) { tickExtensions.add(value); return this; }
        public Builder addModifier(Object value) { modifiers.add(value); return this; }
        public Builder inputItems(TagKey<Item> tag, int count) { this.tag = tag; this.count = count; return this; }
    }
}
