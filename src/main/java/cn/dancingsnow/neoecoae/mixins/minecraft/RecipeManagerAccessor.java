package cn.dancingsnow.neoecoae.mixins.minecraft;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Accessor("byName")
    Map<ResourceLocation, Recipe<?>> neoecoae$recipesByName();

    @Accessor("byName")
    void neoecoae$setRecipesByName(Map<ResourceLocation, Recipe<?>> recipes);

    @Accessor("recipes")
    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> neoecoae$recipesByType();

    @Accessor("recipes")
    void neoecoae$setRecipesByType(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes);
}
