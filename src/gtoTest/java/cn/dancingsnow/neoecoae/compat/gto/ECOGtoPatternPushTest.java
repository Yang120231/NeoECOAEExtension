package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOGtoPatternPushTest {
    public enum Result { SUCCESS, BREAK, REJECTED }
    public static final class Holder { public Object value; }
    public interface NativeProvider {
        Result dispatch(IPatternDetails pattern, Holder input, Supplier<Result> success);
    }

    @Test void acceptedPushStopsTheNativeLoopAndTransfersInputsOnlyOnce() {
        var provider = mock(PatternProviderLogic.class, withSettings().extraInterfaces(NativeProvider.class));
        var pattern = mock(IPatternDetails.class);
        var inputs = new KeyCounter[]{new KeyCounter()};
        when(((NativeProvider) provider).dispatch(eq(pattern), any(), any())).thenAnswer(call -> {
            Holder holder = call.getArgument(1);
            assertSame(inputs, holder.value);
            Supplier<Result> accepted = call.getArgument(2);
            assertEquals(Result.BREAK, accepted.get());
            assertNull(holder.value);
            return Result.BREAK;
        });
        assertTrue(ECOGtoPatternPush.push(provider, pattern, inputs));
        verify((NativeProvider) provider, times(1)).dispatch(eq(pattern), any(), any());
        verify(provider, never()).pushPattern(any(), any());
    }

    @Test void rejectionDoesNotCountAsACommittedPush() {
        var provider = mock(PatternProviderLogic.class, withSettings().extraInterfaces(NativeProvider.class));
        when(((NativeProvider) provider).dispatch(any(), any(), any())).thenReturn(Result.REJECTED);
        assertFalse(ECOGtoPatternPush.push(provider, mock(IPatternDetails.class), new KeyCounter[0]));
    }

    @Test void failureAfterAcceptanceMustNotAuthorizeAnInputRefundOrReplay() {
        var provider = mock(PatternProviderLogic.class, withSettings().extraInterfaces(NativeProvider.class));
        when(((NativeProvider) provider).dispatch(any(), any(), any())).thenAnswer(call -> {
            ((Supplier<?>) call.getArgument(2)).get();
            throw new IllegalStateException("target failed after commit");
        });
        assertThrows(ECOIndeterminateBatchException.class,
                () -> ECOGtoPatternPush.push(provider, mock(IPatternDetails.class), new KeyCounter[0]));
    }

    @Test void specializedProviderUsesItsOverrideAndCallsBackOnceForEachOwnedInputBatch() {
        var provider = mock(PatternProviderLogic.class, withSettings().extraInterfaces(NativeProvider.class));
        var pattern = mock(IPatternDetails.class);
        var first = new KeyCounter[]{new KeyCounter()};
        var second = new KeyCounter[]{new KeyCounter()};
        var holder = new Holder();
        holder.value = first;
        when(provider.pushPattern(eq(pattern), any())).thenReturn(true);
        var callbacks = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(Result.BREAK, ECOGtoPatternPush.dispatchSpecialized(provider, pattern, holder, () -> {
            int count = callbacks.incrementAndGet();
            holder.value = count == 1 ? second : null;
            return count == 1 ? Result.SUCCESS : Result.BREAK;
        }));
        assertEquals(2, callbacks.get());
        verify(provider).pushPattern(pattern, first);
        verify(provider).pushPattern(pattern, second);
    }

    @Test void specializedProviderRejectionDoesNotInvokeSuccessCallback() {
        var provider = mock(PatternProviderLogic.class, withSettings().extraInterfaces(NativeProvider.class));
        var holder = new Holder();
        holder.value = new KeyCounter[0];
        assertEquals(Result.REJECTED, ECOGtoPatternPush.dispatchSpecialized(provider, mock(IPatternDetails.class), holder,
            () -> { throw new AssertionError("Rejected inputs cannot be committed"); }));
    }

    @Test void specializedProviderCallbackFailureAfterCommitIsIndeterminate() {
        var provider = mock(PatternProviderLogic.class, withSettings().extraInterfaces(NativeProvider.class));
        when(provider.pushPattern(any(), any())).thenReturn(true);
        var holder = new Holder();
        holder.value = new KeyCounter[0];
        assertThrows(ECOIndeterminateBatchException.class,
            () -> ECOGtoPatternPush.dispatchSpecialized(provider, mock(IPatternDetails.class), holder,
                () -> { throw new IllegalStateException("failure after acceptance"); }));
    }
}
