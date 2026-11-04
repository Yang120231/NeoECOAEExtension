package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.impl.storage.persistence.StorageAmounts;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Measures allocations of real CPU-inventory IO, not just the underlying arithmetic operation. */
class GtoNativeStorageAllocationTest {
    @Test void longTransfersOnWideStockDoNotAllocateBigIntegers() {
        Assumptions.assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var key = new ECOGtoStorageIdentityTest.Key();
        BigInteger stock = BigInteger.TEN.pow(28);
        var amounts = new StorageAmounts();
        amounts.set(key, HugeAmount.of(stock));
        var inventory = new ECOExactInventory(ignored -> {});
        inventory.setEnabled(true);
        inventory.restore(Map.of(key, stock));
        Runnable physical = () -> {
            for (int i = 0; i < 100_000; i++) {
                amounts.extract(key, 1);
                amounts.add(key, 1);
            }
        };
        Runnable cpu = () -> {
            for (int i = 0; i < 100_000; i++) {
                inventory.extract(key, 1, Actionable.MODULATE);
                inventory.insert(key, 1, Actionable.MODULATE);
            }
        };
        measure(bean, "storage_table", physical);
        measure(bean, "exact_cpu_inventory", cpu);
        assertEquals(stock, amounts.get(key).toBigInteger());
        assertEquals(stock, inventory.amount(key));
    }

    private static void measure(ThreadMXBean bean, String name, Runnable run) {
        for (int i = 0; i < 4; i++) run.run();
        long thread = Thread.currentThread().getId();
        long bestBytes = Long.MAX_VALUE;
        double bestNanos = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 3; i++) {
            long bytes = bean.getThreadAllocatedBytes(thread), started = System.nanoTime();
            run.run();
            long elapsed = System.nanoTime() - started;
            bestBytes = Math.min(bestBytes, bean.getThreadAllocatedBytes(thread) - bytes);
            bestNanos = Math.min(bestNanos, elapsed / 200_000.0);
        }
        System.out.printf(java.util.Locale.ROOT, "%s,ns_per_operation=%.2f,bytes_per_operation=%.4f%n",
            name, bestNanos, bestBytes / 200_000.0);
        assertTrue(bestBytes < 200_000L * 8, name + " allocated " + bestBytes + " bytes for 200000 operations");
    }
}
