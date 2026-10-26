package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.networking.crafting.ICraftingPlan;
import java.util.concurrent.*;

/** Tags a returned GTO plan without adding another executor, blocking, or altering cancellation. */
public record ECOGtoPlanningFuture(Future<ICraftingPlan> delegate) implements Future<ICraftingPlan> {
    @Override public boolean cancel(boolean interrupt) { return delegate.cancel(interrupt); }
    @Override public boolean isCancelled() { return delegate.isCancelled(); }
    @Override public boolean isDone() { return delegate.isDone(); }
    @Override public ICraftingPlan get() throws InterruptedException, ExecutionException { return nativePlan(delegate.get()); }
    @Override public ICraftingPlan get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return nativePlan(delegate.get(timeout, unit));
    }
    private static ICraftingPlan nativePlan(ICraftingPlan plan) {
        if (plan instanceof ECOGtoPlanOrigin origin) origin.neoecoae$markNativeGtoPlan();
        return plan;
    }
}
