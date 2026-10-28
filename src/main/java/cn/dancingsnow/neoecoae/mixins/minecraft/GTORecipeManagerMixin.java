package cn.dancingsnow.neoecoae.mixins.minecraft;

import cn.dancingsnow.neoecoae.compat.gto.ECOGtoRecipes;
import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Runs outside GTO's cancellable HEAD injection, including its warm-cache return. */
@Mixin(value = RecipeManager.class, priority = -100)
public abstract class GTORecipeManagerMixin {
    @Shadow(remap = false) @Final private ICondition.IContext context;

    @WrapMethod(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V")
    private void neoecoae$loadOwnRecipes(Map<ResourceLocation, JsonElement> input, ResourceManager resources,
            ProfilerFiller profiler, Operation<Void> original) {
        var retained = ECOGtoRecipes.capture(resources);
        original.call(input, resources, profiler);
        ECOGtoRecipes.replace((RecipeManager) (Object) this, ECOGtoRecipes.decode(retained, context), "datapack");
    }

    @WrapMethod(method = "replaceRecipes")
    private void neoecoae$syncOwnRecipes(Iterable<Recipe<?>> input, Operation<Void> original) {
        var copy = new ArrayList<Recipe<?>>();
        var retained = new LinkedHashMap<ResourceLocation, Recipe<?>>();
        for (var recipe : input) {
            copy.add(recipe);
            if (ECOGtoRecipes.owns(recipe.getId())) retained.put(recipe.getId(), recipe);
        }
        original.call(copy);
        ECOGtoRecipes.replace((RecipeManager) (Object) this, retained, "network");
    }
}
