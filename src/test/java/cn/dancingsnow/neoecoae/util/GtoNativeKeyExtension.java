package cn.dancingsnow.neoecoae.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.hooks.IUnique;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

/** Supplies only GTO's launcher-installed item/fluid key caches in ordinary JUnit JVMs. */
public final class GtoNativeKeyExtension implements BeforeAllCallback, AfterAllCallback {
    private static final Map<Fluid, Map<CompoundTag, AEFluidKey>> FLUIDS = new IdentityHashMap<>();
    private MockedStatic<AEItemKey> items;
    private MockedStatic<AEFluidKey> fluids;

    @Override public void beforeAll(ExtensionContext context) {
        InventoryTestBootstrap.initialize();
        items = mockStatic(AEItemKey.class, invocation -> {
            Object[] args = invocation.getArguments();
            return switch (invocation.getMethod().getName()) {
                case "of" -> args[0] instanceof ItemStack stack ? GtoKeyTestFactory.item(stack)
                    : GtoKeyTestFactory.item(((ItemLike) args[0]).asItem(), args.length == 2 ? (CompoundTag) args[1] : null);
                case "fromTag" -> {
                    var tag = (CompoundTag) args[0];
                    var item = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(tag.getString("id")))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown item id"));
                    yield GtoKeyTestFactory.item(item, tag.contains("tag", 10) ? tag.getCompound("tag") : null);
                }
                case "fromPacket" -> {
                    var packet = (FriendlyByteBuf) args[0];
                    yield GtoKeyTestFactory.item(BuiltInRegistries.ITEM.byId(packet.readVarInt()), packet.readNbt());
                }
                default -> invocation.callRealMethod();
            };
        });
        fluids = mockStatic(AEFluidKey.class, invocation -> {
            Object[] args = invocation.getArguments();
            return switch (invocation.getMethod().getName()) {
                case "of" -> args[0] instanceof FluidStack stack
                    ? stack.isEmpty() ? null : fluid(stack.getFluid(), stack.getTag())
                    : fluid((Fluid) args[0], args.length == 2 ? (CompoundTag) args[1] : null);
                case "fromTag" -> {
                    var tag = (CompoundTag) args[0];
                    var fluid = BuiltInRegistries.FLUID.getOptional(new ResourceLocation(tag.getString("id")))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown fluid id"));
                    yield fluid(fluid, tag.contains("tag", 10) ? tag.getCompound("tag") : null);
                }
                case "fromPacket" -> {
                    var packet = (FriendlyByteBuf) args[0];
                    yield fluid(BuiltInRegistries.FLUID.byId(packet.readVarInt()), packet.readNbt());
                }
                default -> invocation.callRealMethod();
            };
        });
    }

    private static AEFluidKey fluid(Fluid fluid, CompoundTag tag) {
        CompoundTag copy = tag == null || tag.isEmpty() ? null : tag.copy();
        return FLUIDS.computeIfAbsent(fluid, ignored -> new HashMap<>()).computeIfAbsent(copy, key -> {
            try (var unique = mockStatic(IUnique.class)) {
                unique.when(() -> IUnique.getUid(fluid)).thenReturn(BuiltInRegistries.FLUID.getId(fluid));
                return new AEFluidKey(fluid, key);
            }
        });
    }

    @Override public void afterAll(ExtensionContext context) {
        if (fluids != null) fluids.close();
        if (items != null) items.close();
    }
}
