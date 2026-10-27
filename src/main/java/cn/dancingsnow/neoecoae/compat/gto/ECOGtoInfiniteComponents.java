package cn.dancingsnow.neoecoae.compat.gto;

import java.lang.reflect.Method;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Registration-time identity checks also work before datapack tags have been bound. */
public final class ECOGtoInfiniteComponents {
    private static final ResourceLocation ECO = new ResourceLocation("neoecoae", "eco_infinite_cell_component");
    private static final ResourceLocation GTO = new ResourceLocation("gtocore", "infinite_cell_component");
    private static final TagKey<Item> COMPONENTS = TagKey.create(Registries.ITEM,
            new ResourceLocation("neoecoae", "infinite_cell_components"));
    private static final ClassValue<Method> TAG_INPUT = new ClassValue<>() {
        @Override protected Method computeValue(Class<?> type) {
            try { return type.getMethod("inputItems", TagKey.class, int.class); }
            catch (NoSuchMethodException failure) { throw new IllegalStateException("Missing GTO tag input API", failure); }
        }
    };

    private ECOGtoInfiniteComponents() {}

    public static boolean isComponent(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ECO.equals(id) || GTO.equals(id);
    }

    public static Object addRecipeInput(Object builder, ItemStack stack) {
        try { return TAG_INPUT.get(builder.getClass()).invoke(builder, COMPONENTS, stack.getCount()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot add GTO component alternatives", failure); }
    }

    public static Predicate<ItemStack> storageFilter(Predicate<ItemStack> original) {
        if (original == null) return null;
        return stack -> {
            if (original.test(stack)) return true;
            if (stack.isEmpty() || !ECO.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    || !BuiltInRegistries.ITEM.containsKey(GTO)) return false;
            var equivalent = new ItemStack(BuiltInRegistries.ITEM.get(GTO), stack.getCount());
            if (stack.hasTag()) equivalent.setTag(stack.getTag().copy());
            return original.test(equivalent);
        };
    }
}
