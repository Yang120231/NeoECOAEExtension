package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Uses GTO's native single batch; protected API names are resolved once from their signatures. */
public final class ECOGtoPatternPush {
    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private ECOGtoPatternPush() {}

    public static void verifyContract() {
        Contract.LAYOUT.getClass();
    }

    /** The native success hook runs after insertion, before advancing the batch. */
    public static void accepted(PatternProviderLogic provider, IPatternDetails pattern) {
        State state = STATE.get();
        if (state == null || !state.active || state.provider != provider || state.pattern != pattern) return;
        if (state.accepted) throw new IllegalStateException("GTO accepted the same input batch twice");
        state.accepted = true;
    }

    /** Preserve workstation admission when GTO calls its batch API instead of the AE2 override. */
    public static Object dispatchSpecialized(PatternProviderLogic provider, Object batch) {
        Layout layout = Contract.LAYOUT;
        boolean pushed = false;
        try {
            IPatternDetails pattern = (IPatternDetails) layout.pattern.invokeExact(batch);
            for (int count = 0; count < 1024; count++) {
                KeyCounter[] inputs = (KeyCounter[]) layout.inputs.invokeExact(batch);
                if (inputs == null || !provider.pushPattern(pattern, inputs)) break;
                pushed = true;
                State state = STATE.get();
                if (state != null && state.active && state.provider == provider && state.pattern == pattern
                        && !state.accepted) state.accepted = true;
                if (!(boolean) layout.next.invokeExact(batch)) break;
            }
            return pushed ? layout.success : layout.rejected;
        } catch (Throwable failure) {
            if (failure instanceof Error error) throw error;
            if (pushed) throw new ECOIndeterminateBatchException(
                "GTO batch failed after the workstation accepted its inputs", failure);
            throw new IllegalStateException("GTO workstation batch admission failed", failure);
        }
    }

    /** One scheduler submission consumes exactly one native PushBatch.Single. */
    public static boolean push(PatternProviderLogic provider, IPatternDetails pattern, KeyCounter[] inputs) {
        Layout layout = Contract.LAYOUT;
        State previous = STATE.get();
        State state = previous;
        if (state == null || state.active) {
            state = new State();
            STATE.set(state);
        }
        state.accepted = false;
        state.active = true;
        state.provider = provider;
        state.pattern = pattern;
        try {
            Object batch = (Object) layout.single.invokeExact(pattern, inputs);
            Object result = (Object) layout.push.invokeExact(provider, batch);
            if (result == layout.success && !state.accepted) {
                throw new ECOIndeterminateBatchException(
                    "GTO reported success without its input-commit hook", new IllegalStateException("Missing commit"));
            }
            return state.accepted;
        } catch (Throwable failure) {
            if (failure instanceof Error error) throw error;
            if (failure instanceof ECOIndeterminateBatchException indeterminate) throw indeterminate;
            if (state.accepted) throw new ECOIndeterminateBatchException(
                "GTO failed after accepting pattern inputs", failure);
            throw new IllegalStateException("GTO pattern push failed; accepted=false", failure);
        } finally {
            state.active = false;
            state.provider = null;
            state.pattern = null;
            if (previous != null && previous != state) STATE.set(previous);
        }
    }

    private static final class Contract {
        static final Layout LAYOUT = bind();

        private static Layout bind() {
            Method push = null;
            for (Method method : PatternProviderLogic.class.getMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                        || !method.getReturnType().isEnum()) continue;
                Class<?> batch = method.getParameterTypes()[0];
                if (getter(batch, IPatternDetails.class) == null || getter(batch, KeyCounter[].class) == null
                        || getter(batch, boolean.class) == null) continue;
                if (push != null) throw new IllegalStateException("Ambiguous GTO provider batch API");
                push = method;
            }
            if (push == null) throw new IllegalStateException("GTO's provider batch API has not been applied");
            Class<?> batch = push.getParameterTypes()[0];
            java.lang.reflect.Constructor<?> single = null;
            for (Class<?> type : batch.getDeclaredClasses()) {
                if (!batch.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) continue;
                try {
                    var constructor = type.getConstructor(IPatternDetails.class, KeyCounter[].class);
                    if (single != null) throw new IllegalStateException("Ambiguous GTO single-batch constructor");
                    single = constructor;
                } catch (NoSuchMethodException absent) {
                    // Other native batch kinds have different construction contracts.
                }
            }
            if (single == null) throw new IllegalStateException("GTO's native single batch is unavailable");
            try {
                if (!single.trySetAccessible()) throw new IllegalAccessException("GTO single-batch constructor");
                return new Layout(
                    handle(push, Object.class, PatternProviderLogic.class, Object.class),
                    MethodHandles.lookup().unreflectConstructor(single).asType(
                        MethodType.methodType(Object.class, IPatternDetails.class, KeyCounter[].class)),
                    handle(getter(batch, IPatternDetails.class), IPatternDetails.class, Object.class),
                    handle(getter(batch, KeyCounter[].class), KeyCounter[].class, Object.class),
                    handle(getter(batch, boolean.class), boolean.class, Object.class),
                    result(push.getReturnType(), "SUCCESS"), result(push.getReturnType(), "REJECTED"));
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot bind GTO native batch API", failure);
            }
        }
    }

    private static Method getter(Class<?> type, Class<?> result) {
        Method found = null;
        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                    || method.getReturnType() != result) continue;
            if (found != null) throw new IllegalStateException("Ambiguous GTO batch getter for " + result.getName());
            found = method;
        }
        return found;
    }

    private static MethodHandle handle(Method method, Class<?> result, Class<?>... arguments)
            throws IllegalAccessException {
        if (!method.trySetAccessible()) throw new IllegalAccessException(method.toString());
        return MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(result, arguments));
    }

    private static Object result(Class<?> type, String name) {
        return Arrays.stream(type.getEnumConstants()).filter(value -> ((Enum<?>) value).name().equals(name))
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing GTO push result " + name));
    }

    private record Layout(MethodHandle push, MethodHandle single, MethodHandle pattern, MethodHandle inputs,
                          MethodHandle next, Object success, Object rejected) {}

    private static final class State {
        PatternProviderLogic provider;
        IPatternDetails pattern;
        boolean accepted, active;
    }
}
