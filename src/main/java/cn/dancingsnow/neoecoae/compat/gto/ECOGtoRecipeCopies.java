package cn.dancingsnow.neoecoae.compat.gto;

import java.util.Arrays;
import java.util.Collection;
import net.minecraft.resources.ResourceLocation;

/** Copies the installed GTO definition instead of maintaining a second, potentially cheaper research route. */
final class ECOGtoRecipeCopies {
    private ECOGtoRecipeCopies() {}

    static Object withOutput(Object source, ResourceLocation id, Class<?> builderClass)
            throws ReflectiveOperationException {
        Class<?> definition = source.getClass();
        Object type = definition.getField("recipeType").get(source);
        Object builder = builderClass.getConstructor(ResourceLocation.class, definition.getField("recipeType").getType())
                .newInstance(id, type);
        for (String name : new String[] {"itemInputs", "fluidInputs", "fluidOutputs"}) {
            Object content = definition.getField(name).get(source);
            Object target = builderClass.getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1))
                    .invoke(builder);
            target.getClass().getMethod("addAll", content.getClass()).invoke(target, content);
        }
        builderClass.getMethod("addCondition", Collection.class).invoke(builder,
                Arrays.asList((Object[]) definition.getField("conditions").get(source)));
        for (String[] pair : new String[][] {{"recipeExtensions", "addExtension"},
                {"tickRecipeExtensions", "addTickExtension"}, {"recipeModifiers", "addModifier"}}) {
            var field = definition.getField(pair[0]);
            var add = builderClass.getMethod(pair[1], field.getType().getComponentType());
            for (Object entry : (Object[]) field.get(source)) add.invoke(builder, entry);
        }
        for (String name : new String[] {"recipeCategory", "eut", "tier", "duration", "priority", "chanceFunction", "data"}) {
            Object value = definition.getField(name).get(source);
            if (name.equals("data")) value = value.getClass().getMethod("clone").invoke(value);
            var target = builderClass.getDeclaredField(name);
            target.setAccessible(true);
            target.set(builder, value);
        }
        // save() registers the copy in the type, category and existing data-stick research index.
        return builder;
    }
}
