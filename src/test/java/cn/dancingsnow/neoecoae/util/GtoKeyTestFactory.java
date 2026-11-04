package cn.dancingsnow.neoecoae.util;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.hooks.IUnique;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Interns real GTO item keys in tests that do not launch the Item/ItemStack mixins. */
public final class GtoKeyTestFactory {
    private static final Map<Item, Map<CompoundTag, AEItemKey>> ITEMS = new IdentityHashMap<>();
    private GtoKeyTestFactory() {}

    public static AEItemKey item(Item item) { return item(item, null); }

    public static AEItemKey item(ItemStack stack) {
        return stack.isEmpty() ? null : item(stack.getItem(), stack.getTag());
    }

    public static AEItemKey item(Item item, CompoundTag tag) {
        CompoundTag copy = tag == null || tag.isEmpty() ? null : tag.copy();
        return ITEMS.computeIfAbsent(item, ignored -> new HashMap<>())
            .computeIfAbsent(copy, key -> create(item, key));
    }

    private static AEItemKey create(Item item, CompoundTag tag) {
        try (var unique = org.mockito.Mockito.mockStatic(IUnique.class)) {
            unique.when(() -> IUnique.getUid(item)).thenReturn(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(item));
            return new AEItemKey(item, tag);
        }
    }

    public static AEKey mockKey() {
        return org.mockito.Mockito.mock(AEKey.class, org.mockito.Mockito.withSettings().useConstructor()
            .defaultAnswer(org.mockito.Mockito.RETURNS_DEEP_STUBS));
    }
}
