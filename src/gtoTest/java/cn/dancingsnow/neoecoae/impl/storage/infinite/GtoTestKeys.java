package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Real AEKeys for the GTO fork, whose item and fluid keys need its runtime mixins. Each key has a distinct generic
 * tag, so receipt identities derived from {@code toTagGeneric()} differ per key.
 */
public final class GtoTestKeys {
    private GtoTestKeys() {}

    public static List<AEKey> create(int count) {
        List<AEKey> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) keys.add(new Key("k" + i));
        return keys;
    }

    static final class Key extends AEKey {
        private final String name;
        Key(String name) { this.name = name; }
        @Override public AEKeyType getType() { return AEKeyType.items(); }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "neoecoae_test:" + name);
            return tag;
        }
        @Override public Object getPrimaryKey() { return this; }
        @Override public ResourceLocation getId() { return new ResourceLocation("neoecoae_test", name); }
        @Override public void writeToPacket(FriendlyByteBuf buf) {}
        @Override protected Component computeDisplayName() { return Component.literal(name); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
        @Override public String toString() { return name; }
    }
}
