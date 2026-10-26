package cn.dancingsnow.neoecoae.compat.gto;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import appeng.api.inventories.InternalInventory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Extends GTO's provider-only modifier interaction to ECO while reusing its own scaling implementation. */
@Mod.EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public final class ECOGtoPatternModifier {
    private static final ClassValue<Optional<MethodHandle>> MODIFIERS = new ClassValue<>() {
        @Override protected Optional<MethodHandle> computeValue(Class<?> type) {
            MethodHandle result = null;
            for (var method : type.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != ItemStack.class
                        || !java.util.Arrays.equals(method.getParameterTypes(),
                            new Class<?>[]{ServerPlayer.class, ItemStack.class, ItemStack.class})) continue;
                if (result != null) throw new IllegalStateException("Ambiguous GTO pattern modifier contract");
                try { result = MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflect(method); }
                catch (IllegalAccessException error) { throw new IllegalStateException(error); }
            }
            return Optional.ofNullable(result);
        }
    };

    private ECOGtoPatternModifier() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()
                || !BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString().equals("gtocore:pattern_modifier_pro")
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity host)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            apply(player, event.getItemStack(), host.getWorkstationProvider().getPatternInv());
            host.getWorkstationProvider().updatePatterns();
            host.saveChanges();
            player.displayClientMessage(Component.translatable("gtocore.pattern_modifier_pro.applied", cycles(event.getItemStack())), true);
        } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger("neoecoae").error("GTO pattern modifier could not update ECO patterns", error);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    public static void apply(ServerPlayer player, ItemStack tool, InternalInventory inventory) {
        MethodHandle transform = null;
        try {
            Object components = tool.getItem().getClass().getMethod("getComponents").invoke(tool.getItem());
            for (Object component : (List<?>) components) {
                var candidate = MODIFIERS.get(component.getClass());
                if (candidate.isPresent()) {
                    if (transform != null) throw new IllegalStateException("Ambiguous GTO item modifier component");
                    transform = candidate.get();
                }
            }
            if (transform == null) throw new IllegalStateException("GTO modifier scaling implementation unavailable");
            transform = transform.asType(MethodType.methodType(ItemStack.class, ServerPlayer.class, ItemStack.class, ItemStack.class));
            for (int cycle = 0; cycle < cycles(tool); cycle++) {
                ItemStack[] replacement = new ItemStack[inventory.size()];
                for (int slot = 0; slot < replacement.length; slot++) {
                    var stack = inventory.getStackInSlot(slot);
                    if (!stack.isEmpty()) replacement[slot] = (ItemStack) transform.invokeExact(player, tool, stack.copy());
                }
                for (int slot = 0; slot < replacement.length; slot++) {
                    if (replacement[slot] != null) inventory.setItemDirect(slot, replacement[slot]);
                }
            }
        } catch (Throwable error) {
            if (error instanceof Error fatal) throw fatal;
            throw new IllegalStateException("Cannot apply GTO native pattern scaling", error);
        }
    }

    private static int cycles(ItemStack tool) {
        var tag = tool.getTag();
        long count = tag != null && tag.contains("Cycles", 4) ? tag.getLong("Cycles") : 1;
        return (int) Math.max(1, Math.min(16, count));
    }
}
