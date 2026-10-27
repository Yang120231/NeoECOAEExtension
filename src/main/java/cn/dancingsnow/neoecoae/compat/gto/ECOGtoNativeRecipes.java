package cn.dancingsnow.neoecoae.compat.gto;

import cn.dancingsnow.neoecoae.config.RecipeProfileConfig;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.fluids.FluidStack;

/** Registers the approved GT progression recipes through GTO's native immutable definition builder. */
public final class ECOGtoNativeRecipes {
    private static int registered;
    private static volatile Map<ResourceLocation, Object> registeredDefinitions = Map.of();
    private ECOGtoNativeRecipes() {}
    public static int registeredCount() { return registered; }
    public static Map<ResourceLocation, Object> registeredDefinitions() { return registeredDefinitions; }

    static ResourceLocation registeredId(ResourceLocation builderId, ResourceLocation typeId) {
        return builderId.withPrefix(typeId.getPath() + "/");
    }

    public static void register() {
        if (RecipeProfileConfig.profile() == RecipeProfileConfig.Profile.STANDALONE) return;
        String resource = "/recipe_profiles/gt_gto/native_recipes.json";
        try (var input = ECOGtoNativeRecipes.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing GTO native recipe definitions");
            var rows = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("recipes");
            var registries = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
            Object types = registries.getField("RECIPE_TYPES").get(null);
            var getType = types.getClass().getMethod("get", Comparable.class);
            var typeClass = Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipeType");
            var builderClass = Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipeBuilder");
            var create = typeClass.getMethod("recipeBuilder", ResourceLocation.class);
            var itemInput = builderClass.getMethod("inputItems", Item.class, int.class);
            var tagInput = builderClass.getMethod("inputItems", TagKey.class, int.class);
            var itemOutput = builderClass.getMethod("outputItems", Item.class, int.class);
            var fluidInput = builderClass.getMethod("inputFluids", FluidStack.class);
            var fluidOutput = builderClass.getMethod("outputFluids", FluidStack.class);
            var power = builderClass.getMethod("EUt", long.class);
            var duration = builderClass.getMethod("duration", int.class);
            var blast = builderClass.getMethod("blastFurnaceTemp", int.class);
            var cleanroom = builderClass.getMethod("cleanroom", int.class);
            var save = builderClass.getMethod("save");
            var definitions = (java.util.Map<?, ?>) Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition")
                    .getField("RECIPES").get(null);
            int count = 0;
            var published = new LinkedHashMap<ResourceLocation, Object>();
            for (var value : rows) {
                var row = value.getAsJsonObject();
                var recipeId = new ResourceLocation(row.get("id").getAsString());
                Object source = null;
                Object type;
                if (row.has("copy_from")) {
                    var sourceId = new ResourceLocation(row.get("copy_from").getAsString());
                    source = definitions.get(sourceId);
                    if (source == null) throw new IllegalStateException("Missing GTO source recipe " + sourceId);
                    type = source.getClass().getField("recipeType").get(source);
                } else {
                    type = getType.invoke(types, new ResourceLocation(row.get("recipe_type").getAsString()));
                    if (type == null) throw new IllegalStateException("Missing GTO recipe type " + row.get("recipe_type"));
                }
                var typeId = (ResourceLocation) typeClass.getField("registryName").get(type);
                var finalId = registeredId(recipeId, typeId);
                if (definitions.containsKey(finalId)) {
                    published.put(finalId, definitions.get(finalId));
                    count++;
                    continue;
                }
                if (source != null) {
                    var output = row.getAsJsonObject("item_output");
                    Object builder = ECOGtoRecipeCopies.withOutput(source, recipeId, builderClass);
                    itemOutput.invoke(builder, item(output), output.get("count").getAsInt());
                    published.put(finalId, save.invoke(builder));
                    count++;
                    continue;
                }
                Object builder = create.invoke(type, recipeId);
                for (var raw : row.getAsJsonArray("item_inputs")) {
                    var ingredient = raw.getAsJsonObject();
                    int amount = ingredient.get("count").getAsInt();
                    if (ingredient.has("tag")) {
                        tagInput.invoke(builder, TagKey.create(Registries.ITEM,
                                new ResourceLocation(ingredient.get("tag").getAsString())), amount);
                    } else {
                        itemInput.invoke(builder, item(ingredient), amount);
                    }
                }
                if (row.has("item_output")) {
                    var output = row.getAsJsonObject("item_output");
                    itemOutput.invoke(builder, item(output), output.get("count").getAsInt());
                }
                if (row.has("fluid_output")) {
                    fluidOutput.invoke(builder, fluid(row.getAsJsonObject("fluid_output")));
                }
                power.invoke(builder, row.get("eu_per_tick").getAsLong());
                duration.invoke(builder, row.get("ticks").getAsInt());
                if (row.has("input_fluid")) {
                    fluidInput.invoke(builder, fluid(row.getAsJsonObject("input_fluid")));
                }
                if (row.has("blast_temperature")) blast.invoke(builder, row.get("blast_temperature").getAsInt());
                if (row.has("conditions")) {
                    for (var raw : row.getAsJsonArray("conditions")) {
                        String room = raw.getAsJsonObject().get("cleanroom").getAsString();
                        if (!room.equals("cleanroom")) throw new IllegalStateException("Unsupported GTO cleanroom " + room);
                        cleanroom.invoke(builder, 1);
                    }
                }
                published.put(finalId, save.invoke(builder));
                count++;
            }
            registered = count;
            registeredDefinitions = Map.copyOf(published);
            org.slf4j.LoggerFactory.getLogger("neoecoae").info("Registered {} GTO native GT progression recipes", count);
        } catch (ReflectiveOperationException | java.io.IOException failure) {
            throw new IllegalStateException("Cannot register GTO native progression recipes", failure);
        }
    }

    private static Item item(JsonObject definition) {
        var id = new ResourceLocation(definition.get("item").getAsString());
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalStateException("Missing GTO recipe item " + id);
        return BuiltInRegistries.ITEM.get(id);
    }

    private static FluidStack fluid(JsonObject definition) {
        var id = new ResourceLocation(definition.get("fluid").getAsString());
        if (!BuiltInRegistries.FLUID.containsKey(id)) throw new IllegalStateException("Missing GTO fluid " + id);
        return new FluidStack(BuiltInRegistries.FLUID.get(id), definition.get("amount").getAsInt());
    }
}
