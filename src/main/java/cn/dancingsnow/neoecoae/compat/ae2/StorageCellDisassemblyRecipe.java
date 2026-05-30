package cn.dancingsnow.neoecoae.compat.ae2;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A datagen {@link FinishedRecipe} that represents storage cell disassembly.
 * <p>
 * In vanilla shapeless terms: the storage cell is the single ingredient,
 * and the cell housing is the result. The cell component is consumed and not
 * returned (vanilla recipes can only have a single output).
 * </p>
 * <p>
 * For regular item/fluid cells, this recipe is saved directly via
 * {@code prov.accept(recipe)} and its ID is derived from the cell item name.
 * For AppMek chemical cells, the recipe is wrapped in
 * {@link net.minecraftforge.common.crafting.ConditionalRecipe} with a
 * {@code forge:mod_loaded("appmek")} condition and a caller-supplied ID.
 * </p>
 */
public class StorageCellDisassemblyRecipe implements FinishedRecipe {
    private final Item result;
    private final List<ItemStack> inputs;

    /**
     * @param result the item produced by disassembly (typically the cell housing)
     * @param inputs the items consumed (typically just the storage cell itself)
     */
    public StorageCellDisassemblyRecipe(Item result, List<ItemStack> inputs) {
        this.result = result;
        this.inputs = inputs;
    }

    /**
     * Runtime disassembly lookup. Currently returns an empty list;
     * a full implementation would query the {@code RecipeManager}.
     */
    public static List<ItemStack> getDisassemblyResult(Level level, Item item) {
        return List.of();
    }

    @Override
    public void serializeRecipeData(JsonObject json) {
        JsonArray ingredients = new JsonArray();
        for (ItemStack stack : inputs) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            ingredients.add(entry);
        }
        json.add("ingredients", ingredients);

        JsonObject resultObj = new JsonObject();
        resultObj.addProperty("item", BuiltInRegistries.ITEM.getKey(result).toString());
        resultObj.addProperty("count", 1);
        json.add("result", resultObj);
    }

    @Override
    public ResourceLocation getId() {
        // Derive the ID from the first input item (the cell being disassembled).
        Item firstInput = inputs.isEmpty() ? result : inputs.get(0).getItem();
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(firstInput);
        return new ResourceLocation(key.getNamespace(), "disassembly/" + key.getPath());
    }

    @Override
    public RecipeSerializer<?> getType() {
        return RecipeSerializer.SHAPELESS_RECIPE;
    }

    @Override
    public JsonObject serializeAdvancement() {
        return null;
    }

    @Override
    public ResourceLocation getAdvancementId() {
        return null;
    }
}
