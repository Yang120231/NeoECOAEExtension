package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import com.gto.datasynclib.util.holder.ObjHolder;
import com.gtolib.api.ae2.IPatternProviderLogic;
import com.gtolib.api.ae2.IPatternProviderLogic.PushResult;
import java.util.function.Supplier;

/** Direct adapter for GTO's callback-based provider API. */
public final class ECOGtoPatternPush {
    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private ECOGtoPatternPush() {}

    public static void verifyContract() {
        if (!IPatternProviderLogic.class.isAssignableFrom(PatternProviderLogic.class)) {
            throw new IllegalStateException("GTO's pattern-provider API has not been applied");
        }
    }

    /** Mixin's coerced boundary; the holder and callback are GTO's public API objects. */
    @SuppressWarnings("unchecked")
    public static Object dispatchSpecialized(PatternProviderLogic provider, IPatternDetails pattern,
            Object holder, Supplier<?> accepted) {
        ObjHolder<KeyCounter[]> inputs = (ObjHolder<KeyCounter[]>) holder;
        boolean pushed = false;
        for (int count = 0; count < 1024; count++) {
            if (inputs.value == null || !provider.pushPattern(pattern, inputs.value)) break;
            pushed = true;
            Object result;
            try {
                result = accepted.get();
            } catch (Throwable failure) {
                if (failure instanceof Error error) throw error;
                throw new ECOIndeterminateBatchException(
                    "GTO callback failed after the workstation accepted its inputs", failure);
            }
            if (result != PushResult.SUCCESS) return result;
        }
        return pushed ? PushResult.SUCCESS : PushResult.REJECTED;
    }

    /** One scheduler submission owns one input batch; GTO's callback ends its native loop after commit. */
    public static boolean push(PatternProviderLogic provider, IPatternDetails pattern, KeyCounter[] inputs) {
        IPatternProviderLogic nativeProvider = (IPatternProviderLogic) provider;
        State previous = STATE.get();
        State state = previous;
        if (state == null || state.active) {
            state = new State();
            STATE.set(state);
        }
        state.accepted = false;
        state.active = true;
        state.holder.value = inputs;
        try {
            nativeProvider.gtolib$pushPattern(pattern, state.holder, state);
            return state.accepted;
        } catch (Throwable failure) {
            if (failure instanceof Error error) throw error;
            if (state.accepted) throw new ECOIndeterminateBatchException(
                    "GTO failed after accepting pattern inputs", failure);
            throw new IllegalStateException("GTO pattern push failed; accepted=false", failure);
        } finally {
            state.active = false;
            state.holder.value = null;
            if (previous != null && previous != state) STATE.set(previous);
        }
    }

    private static final class State implements Supplier<PushResult> {
        final ObjHolder<KeyCounter[]> holder = new ObjHolder<>();
        boolean accepted, active;

        @Override public PushResult get() {
            if (accepted) throw new IllegalStateException("GTO accepted the same input batch twice");
            accepted = true;
            holder.value = null;
            return PushResult.BREAK;
        }
    }
}
