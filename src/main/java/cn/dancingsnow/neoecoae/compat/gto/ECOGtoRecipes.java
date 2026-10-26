package cn.dancingsnow.neoecoae.compat.gto;

import cn.dancingsnow.neoecoae.mixins.minecraft.RecipeManagerAccessor;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.slf4j.LoggerFactory;

/** Keeps ECO datapack recipes independent of GTO's cached recipe catalogue. */
public final class ECOGtoRecipes {
    private ECOGtoRecipes() {}

    public static boolean owns(ResourceLocation id) { return id.getNamespace().equals("neoecoae"); }

    public static Map<ResourceLocation, JsonElement> capture(net.minecraft.server.packs.resources.ResourceManager resources) {
        var converter = net.minecraft.resources.FileToIdConverter.json("recipes");
        var result = new LinkedHashMap<ResourceLocation, JsonElement>();
        // GTO also caches SimpleJsonResourceReloadListener.scanDirectory, before RecipeManager.apply.
        // Query the current enabled packs directly; listResources already resolves pack priority and filters.
        resources.listResources("recipes", id -> owns(id) && id.getPath().endsWith(".json")).forEach((file, resource) -> {
            var id = converter.fileToId(file);
            if (id.getPath().startsWith("_")) return;
            try (var reader = resource.openAsReader()) {
                result.put(id, com.google.gson.JsonParser.parseReader(reader));
            } catch (java.io.IOException | com.google.gson.JsonParseException failure) {
                LoggerFactory.getLogger("neoecoae").error("Cannot read GTO ECO recipe {}", id, failure);
            }
        });
        return result;
    }

    public static Map<ResourceLocation, Recipe<?>> decode(Map<ResourceLocation, JsonElement> input,
            ICondition.IContext context) {
        var result = new LinkedHashMap<ResourceLocation, Recipe<?>>();
        input.forEach((id, json) -> {
            try {
                var object = GsonHelper.convertToJsonObject(json, "top element");
                if (!CraftingHelper.processConditions(object, "conditions", context)) return;
                var recipe = RecipeManager.fromJson(id, object, context);
                if (recipe != null) result.put(id, recipe);
            } catch (IllegalArgumentException | com.google.gson.JsonParseException failure) {
                // Same per-recipe failure policy as Forge's loader, without hiding bad ECO definitions.
                LoggerFactory.getLogger("neoecoae").error("Cannot load GTO ECO recipe {}", id, failure);
            }
        });
        return result;
    }

    public static void replace(RecipeManager manager, Map<ResourceLocation, Recipe<?>> additions, String side) {
        var access = (RecipeManagerAccessor) manager;
        var names = new HashMap<>(access.neoecoae$recipesByName());
        names.keySet().removeIf(ECOGtoRecipes::owns);
        names.putAll(additions);
        // Rebuild both indices together. GTO's shared cached maps must never be mutated in place.
        var types = new HashMap<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>();
        names.forEach((id, recipe) -> types.computeIfAbsent(recipe.getType(), ignored -> new HashMap<>()).put(id, recipe));
        types.replaceAll((type, recipes) -> Map.copyOf(recipes));
        access.neoecoae$setRecipesByType(Map.copyOf(types));
        access.neoecoae$setRecipesByName(Map.copyOf(names));
        LargeWorkstationRecipes.invalidate();
        LoggerFactory.getLogger("neoecoae").info("GTO ECO recipes: side={}, loaded={}", side, additions.size());
    }
}
