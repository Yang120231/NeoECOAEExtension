package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/** Single-push adapter for GTO's callback-based provider API, including protected release names. */
public final class ECOGtoPatternPush {
    private static final ClassValue<Layout> LAYOUTS = new ClassValue<>() {
        @Override protected Layout computeValue(Class<?> type) { return new Layout(type); }
    };
    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private ECOGtoPatternPush() {}

    public static void verifyContract() { LAYOUTS.get(PatternProviderLogic.class); }

    /** Retains a specialized provider's virtual dispatch when GTO calls its new native callback API. */
    public static Object dispatchSpecialized(PatternProviderLogic provider, IPatternDetails pattern,
            Object holder, Supplier<?> accepted) {
        Layout layout = LAYOUTS.get(provider.getClass());
        boolean pushed = false;
        for (int count = 0; count < 1024; count++) {
            var inputs = (KeyCounter[]) layout.value.get(holder);
            if (inputs == null || !provider.pushPattern(pattern, inputs)) break;
            pushed = true;
            Object result;
            try {
                result = accepted.get();
            } catch (Throwable failure) {
                if (failure instanceof Error error) throw error;
                throw new cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException(
                    "GTO callback failed after the workstation accepted its inputs", failure);
            }
            if (result != layout.success) return result;
        }
        return pushed ? layout.success : layout.rejected;
    }

    public static boolean push(PatternProviderLogic provider, IPatternDetails pattern, KeyCounter[] inputs) {
        Layout layout = LAYOUTS.get(provider.getClass());
        State previous = STATE.get();
        State state = previous;
        if (state == null || state.active || state.layout != layout) {
            state = new State(layout);
            STATE.set(state);
        }
        state.accepted = false;
        state.active = true;
        layout.value.set(state.holder, inputs);
        try {
            Object ignored = (Object) layout.push.invokeExact((Object) provider, pattern, state.holder, (Supplier<?>) state);
            return state.accepted;
        } catch (Throwable failure) {
            if (failure instanceof Error error) throw error;
            if (state.accepted) throw new cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException(
                    "GTO failed after accepting pattern inputs", failure);
            throw new IllegalStateException("GTO pattern push failed; accepted=" + state.accepted, failure);
        } finally {
            state.active = false;
            layout.value.set(state.holder, null);
            if (previous != null && previous != state) STATE.set(previous);
        }
    }

    private static final class State implements Supplier<Object> {
        final Layout layout;
        final Object holder;
        boolean accepted, active;
        State(Layout layout) {
            this.layout = layout;
            try { holder = (Object) layout.holder.invokeExact(); }
            catch (Throwable failure) { throw new IllegalStateException("Cannot create GTO input holder", failure); }
        }
        @Override public Object get() {
            if (accepted) throw new IllegalStateException("GTO accepted the same input batch twice");
            accepted = true;
            layout.value.set(holder, null);
            return layout.stop;
        }
    }

    private static final class Layout {
        final MethodHandle push, holder;
        final VarHandle value;
        final Object stop, success, rejected;
        Layout(Class<?> provider) {
            Method api = null;
            for (Method method : provider.getMethods()) {
                Class<?>[] arguments = method.getParameterTypes();
                if (arguments.length == 3 && arguments[0] == IPatternDetails.class
                        && arguments[2] == Supplier.class && method.getReturnType().isEnum()) {
                    if (api != null) throw new IllegalStateException("Ambiguous GTO provider callback contract");
                    api = method;
                }
            }
            if (api == null) throw new IllegalStateException("Missing GTO provider callback contract");
            Object stopResult = null, successResult = null, rejectedResult = null;
            for (Object result : api.getReturnType().getEnumConstants()) {
                if (((Enum<?>) result).name().equals("BREAK")) stopResult = result;
                if (((Enum<?>) result).name().equals("SUCCESS")) successResult = result;
                if (((Enum<?>) result).name().equals("REJECTED")) rejectedResult = result;
            }
            if (stopResult == null) throw new IllegalStateException("GTO single-push stop result is unavailable");
            stop = stopResult;
            if (successResult == null || rejectedResult == null) throw new IllegalStateException("Incomplete GTO provider result contract");
            success = successResult;
            rejected = rejectedResult;
            try {
                var lookup = MethodHandles.privateLookupIn(provider, MethodHandles.lookup());
                push = lookup.unreflect(api).asType(MethodType.methodType(Object.class,
                        Object.class, IPatternDetails.class, Object.class, Supplier.class));
                Class<?> holderType = api.getParameterTypes()[1];
                var holderLookup = MethodHandles.privateLookupIn(holderType, MethodHandles.lookup());
                var constructor = holderLookup.findConstructor(holderType, MethodType.methodType(void.class));
                holder = constructor.asType(MethodType.methodType(Object.class));
                value = holderLookup.findVarHandle(holderType, "value", Object.class);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot bind GTO provider callback contract", failure);
            }
        }
    }
}
