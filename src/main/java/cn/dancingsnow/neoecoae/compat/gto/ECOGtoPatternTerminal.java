package cn.dancingsnow.neoecoae.compat.gto;

import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.RestrictedInputSlot;
import cn.dancingsnow.neoecoae.crafting.PatternTerminalUpload;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Binds the protected GTO encoding module through its public slot and mode contracts. */
public final class ECOGtoPatternTerminal {
    private static final ClassValue<Encoding> ENCODINGS = new ClassValue<>() {
        @Override protected Encoding computeValue(Class<?> type) {
            Method encoding = null;
            Method slots = null;
            Method mode = null;
            for (Method getter : type.getMethods()) {
                if (Modifier.isStatic(getter.getModifiers()) || getter.getParameterCount() != 0) continue;
                // The root's mode drives the tabs; the encoding module's recipe mode is nullable.
                if (getter.getReturnType().isEnum() && Arrays.stream(getter.getReturnType().getEnumConstants())
                        .anyMatch(value -> ((Enum<?>) value).name().equals("SMITHING_TABLE"))) {
                    if (mode != null) throw new IllegalStateException("Ambiguous GTO terminal mode");
                    mode = getter;
                }
                if (!MEStorageMenu.class.isAssignableFrom(getter.getReturnType())) continue;
                Method encodedSlots = null;
                for (Method member : getter.getReturnType().getMethods()) {
                    if (Modifier.isStatic(member.getModifiers()) || member.getParameterCount() != 0) continue;
                    if (member.getReturnType() == RestrictedInputSlot[].class) {
                        if (encodedSlots != null) throw new IllegalStateException("Ambiguous GTO encoded-pattern slots");
                        encodedSlots = member;
                    }
                }
                if (encodedSlots == null) continue;
                if (encoding != null) throw new IllegalStateException("Ambiguous GTO encoding module");
                encoding = getter;
                slots = encodedSlots;
            }
            if (encoding == null) throw new IllegalStateException("GTO encoding module is unavailable");
            if (mode == null) throw new IllegalStateException("GTO terminal mode is unavailable");
            try {
                return new Encoding(handle(encoding, MEStorageMenu.class), handle(slots, RestrictedInputSlot[].class),
                    handle(mode, Enum.class));
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot bind GTO pattern terminal", failure);
            }
        }
    };

    private ECOGtoPatternTerminal() {}

    public static boolean isMenu(AEBaseMenu menu) {
        for (Class<?> type = menu.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().startsWith("com.gtolib.") && type.getSuperclass() != null
                    && type.getSuperclass().getName().equals(
                        "com.glodblock.github.extendedae.container.ContainerExPatternTerminal")) return true;
        }
        return false;
    }

    public static void upload(AEBaseMenu menu) {
        if (!menu.isValidMenu()) return;
        Encoding access = ENCODINGS.get(menu.getClass());
        try {
            MEStorageMenu encoding = (MEStorageMenu) access.module.invokeExact((Object) menu);
            var node = encoding.getNetworkNode();
            if (node == null || !node.isActive()) return;
            Enum<?> mode = (Enum<?>) access.mode.invokeExact((Object) menu);
            if (!canUpload(mode)) return;
            for (RestrictedInputSlot slot : (RestrictedInputSlot[]) access.slots.invokeExact((Object) encoding)) {
                PatternTerminalUpload.upload(node.getGrid(), menu.getPlayer(), slot);
            }
        } catch (Throwable failure) {
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("GTO pattern upload failed", failure);
        }
    }

    /** The client button and server admission use the same native terminal mode. */
    public static boolean canUpload(AEBaseMenu menu) {
        try {
            return canUpload((Enum<?>) ENCODINGS.get(menu.getClass()).mode.invokeExact((Object) menu));
        } catch (Throwable failure) {
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("Cannot read GTO terminal mode", failure);
        }
    }

    private static boolean canUpload(Enum<?> mode) {
        return mode != null && (mode.name().equals("CRAFTING") || mode.name().equals("SMITHING_TABLE")
            || mode.name().equals("STONECUTTING"));
    }

    private static MethodHandle handle(Method method, Class<?> result) throws IllegalAccessException {
        if (!method.trySetAccessible()) throw new IllegalAccessException(method.toString());
        return MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(result, Object.class));
    }

    private record Encoding(MethodHandle module, MethodHandle slots, MethodHandle mode) {}
}
