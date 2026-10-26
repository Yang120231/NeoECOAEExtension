package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.networking.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOGtoPlanningFutureTest {
    @Test void bothGetMethodsPreservePlanIdentityAndMarkItsNativeOrigin() throws Exception {
        var plan = mock(ICraftingPlan.class, withSettings().extraInterfaces(ECOGtoPlanOrigin.class));
        var future = new ECOGtoPlanningFuture(CompletableFuture.completedFuture(plan));
        assertSame(plan, future.get());
        assertSame(plan, future.get(1, TimeUnit.SECONDS));
        verify((ECOGtoPlanOrigin) plan, times(2)).neoecoae$markNativeGtoPlan();
        assertTrue(future.isDone());
    }

    @Test void cancellationAndTimeoutRemainOwnedByTheOriginalExecutor() throws Exception {
        Future<ICraftingPlan> delegate = mock(Future.class);
        when(delegate.get(2, TimeUnit.MILLISECONDS)).thenThrow(new TimeoutException());
        when(delegate.cancel(true)).thenReturn(true);
        when(delegate.isCancelled()).thenReturn(true);
        var future = new ECOGtoPlanningFuture(delegate);
        assertThrows(TimeoutException.class, () -> future.get(2, TimeUnit.MILLISECONDS));
        assertTrue(future.cancel(true));
        assertTrue(future.isCancelled());
        verify(delegate).cancel(true);
    }

    @Test void nativeOriginCannotInheritEcoDiagnosticsFromAnEquivalentTaskVector() {
        var plan = mock(ICraftingPlan.class, withSettings().extraInterfaces(ECOGtoPlanOrigin.class));
        when(((ECOGtoPlanOrigin) plan).neoecoae$isNativeGtoPlan()).thenReturn(true);
        assertNull(ECOPlanningResultRegistry.find(plan));
        assertNull(ECOPlanningResultRegistry.findExact(plan));
        assertFalse(ECOPlanningResultRegistry.isECOOwnedPlan(plan));
        verify(plan, never()).patternTimes();
    }
}
