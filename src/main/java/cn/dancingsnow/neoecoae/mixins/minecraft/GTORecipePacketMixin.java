package cn.dancingsnow.neoecoae.mixins.minecraft;

import cn.dancingsnow.neoecoae.compat.gto.ECOGtoRecipes;
import cn.dancingsnow.neoecoae.platform.mixin.ObserveGtoPattern;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GTO may discard the packet on a warm cache; ECO must still receive the server's current datapack. */
@Mixin(value = ClientboundUpdateRecipesPacket.class, priority = 500)
public abstract class GTORecipePacketMixin {
    @Unique private List<Recipe<?>> neoecoae$incomingRecipes;

    @ObserveGtoPattern(helper = "readList", at = @At("HEAD"))
    private void neoecoae$captureIncoming(FriendlyByteBuf buffer, FriendlyByteBuf.Reader<Recipe<?>> reader,
            CallbackInfoReturnable<List<Recipe<?>>> ci) {
        // Independent indices, shared bytes, synchronous use: no extra retain/release or original-buffer mutation.
        var copy = new FriendlyByteBuf(buffer.duplicate());
        neoecoae$incomingRecipes = copy.readList(reader).stream()
            .filter(recipe -> ECOGtoRecipes.owns(recipe.getId())).toList();
    }

    @ObserveGtoPattern(helper = "readList", at = @At("RETURN"), cancellable = true)
    private void neoecoae$restoreIncoming(CallbackInfoReturnable<List<Recipe<?>>> ci) {
        var merged = new LinkedHashMap<ResourceLocation, Recipe<?>>();
        for (var recipe : ci.getReturnValue()) merged.put(recipe.getId(), recipe);
        for (var recipe : neoecoae$incomingRecipes) merged.put(recipe.getId(), recipe);
        neoecoae$incomingRecipes = null;
        ci.setReturnValue(List.copyOf(merged.values()));
    }
}
