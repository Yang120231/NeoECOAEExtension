package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.CompiledPatternKey;
import cn.dancingsnow.neoecoae.api.me.CompiledPatternResult;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ECOCraftingWorkerBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingWorkerBlockEntity>
    implements IGridTickable {

    /** Max entries in the compiled-pattern LRU cache. */
    private static final int COMPILED_CACHE_MAX = 512;

    // ── Thread pools (replaces old single craftingThreads list) ──

    /** Threads currently executing a pattern. */
    private final List<ECOCraftingThread> busyThreads = new ArrayList<>();

    /** Idle threads available for new work. */
    private final ArrayDeque<ECOCraftingThread> freeThreads = new ArrayDeque<>();

    /** Total threads ever created (busy + free). Capped by controller limit. */
    private int totalThreadCount;

    // ── Compiled-pattern cache ──

    /**
     * LRU cache mapping {@link CompiledPatternKey} → {@link CompiledPatternResult}.
     * Transient (never saved to NBT). Cleared on cluster change or world load.
     */
    private final LinkedHashMap<CompiledPatternKey, CompiledPatternResult> compiledCache =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CompiledPatternKey, CompiledPatternResult> eldest) {
                    return size() > COMPILED_CACHE_MAX;
                }
            };

    // ── Pending eject buffer (structure prepared for future batch optimization) ──

    @Getter
    private int runningThreads = 0;

    public ECOCraftingWorkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        getMainNode().addService(IGridTickable.class, this);
    }

    // ── Cache management ──

    /** Clears the compiled pattern cache. Call on cluster change or world reload. */
    public void clearCompiledPatternCache() {
        compiledCache.clear();
    }

    // ── Grid tickable ──

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(64);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // If we have busy threads, request faster ticking; otherwise idle.
        return busyThreads.isEmpty()
                ? new TickingRequest(10, 20, false, false)
                : new TickingRequest(1, 10, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (cluster == null || cluster.getController() == null) {
            return TickRateModulation.IDLE;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        int powerMultiply = 1;
        if (controller.isOverclocked() && !controller.isActiveCooling()) {
            powerMultiply = controller.getTier().getOverclockedCrafterPowerMultiply();
        }
        int overlockTimes = controller.getEffectiveOverclockTimes();

        TickRateModulation rate = TickRateModulation.IDLE;

        // Only iterate busy threads — free threads need no ticking.
        Iterator<ECOCraftingThread> it = busyThreads.iterator();
        while (it.hasNext()) {
            ECOCraftingThread thread = it.next();
            TickRateModulation r = thread.tick(overlockTimes, powerMultiply, ticksSinceLastCall);
            if (r.ordinal() > rate.ordinal()) {
                rate = r;
            }
            // Thread finished and successfully ejected — move to free pool.
            // (onThreadStop was already called inside thread.tick → ejectOutputs)
            if (thread.isFree()) {
                it.remove();
                freeThreads.addLast(thread);
            }
            // If still busy (ejectOutputs failed), remains in busyThreads for retry.
        }

        setChanged();
        return rate;
    }

    // ── Pattern push ──

    public boolean pushPattern(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] table) {
        if (cluster == null || cluster.getController() == null) {
            return false;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        if (runningThreads >= controller.getThreadCountPerWorker()) {
            return false;
        }

        // Try cache first.
        CompiledPatternKey cacheKey = new CompiledPatternKey(pattern, table);
        CompiledPatternResult cached = compiledCache.get(cacheKey);

        ECOCraftingThread thread = obtainThread(controller);
        if (thread == null) {
            return false;
        }

        boolean success;
        if (cached != null) {
            success = thread.pushPatternCached(cached, controller);
        } else {
            success = thread.pushPattern(pattern, table, controller);
        }

        if (success) {
            busyThreads.add(thread);
            // On cache miss, snapshot the thread's result for future reuse.
            if (cached == null) {
                CompiledPatternResult result = thread.snapshotResult();
                if (result != null) {
                    compiledCache.put(cacheKey, result);
                }
            }
            return true;
        } else {
            // Push failed — return thread to free pool.
            freeThreads.addFirst(thread);
            return false;
        }
    }

    /**
     * Obtains an idle thread, creating a new one if needed and allowed.
     *
     * @return a free thread, or {@code null} if no thread is available
     */
    private ECOCraftingThread obtainThread(ECOCraftingSystemBlockEntity controller) {
        // Prefer existing free thread.
        ECOCraftingThread free = freeThreads.pollFirst();
        if (free != null) {
            return free;
        }
        // Create new if under the per-worker cap.
        if (totalThreadCount < controller.getThreadCountPerWorker()) {
            ECOCraftingThread thread = new ECOCraftingThread(this);
            totalThreadCount++;
            setChanged();
            markForUpdate();
            return thread;
        }
        return null;
    }

    // ── Busy check (side-effect free) ──

    public boolean isBusy() {
        if (cluster == null || cluster.getController() == null) {
            return true;
        }
        ECOCraftingSystemBlockEntity controller = cluster.getController();
        if (runningThreads >= controller.getThreadCountPerWorker()) {
            return true;
        }
        if (!freeThreads.isEmpty()) {
            return false;
        }
        if (totalThreadCount < controller.getThreadCountPerWorker()) {
            return false;
        }
        return true;
    }

    // ── Thread lifecycle callbacks ──

    public void onThreadWork() {
        runningThreads++;
        setChanged();
        markForUpdate();
        wakeTickingDevice();
    }

    @Override
    public void setChanged() {
        if (this.level != null) {
            level.blockEntityChanged(getBlockPos());
        }
    }

    public void onThreadStop() {
        if (runningThreads > 0) {
            runningThreads--;
        }
        setChanged();
        markForUpdate();
    }

    private void wakeTickingDevice() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    // ── NBT persistence ──

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        ListTag threads = new ListTag();
        // Save busy threads (they have in-progress state).
        for (ECOCraftingThread thread : busyThreads) {
            threads.add(thread.serializeNBT());
        }
        // Free threads are NOT saved — they have no in-progress state.
        data.put("craftingThreads", threads);
        data.putInt("runningThreads", runningThreads);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        // Clear existing state.
        busyThreads.clear();
        freeThreads.clear();
        compiledCache.clear();

        ListTag threads = data.getList("craftingThreads", Tag.TAG_COMPOUND);
        for (int i = 0; i < threads.size(); i++) {
            ECOCraftingThread thread = new ECOCraftingThread(this);
            thread.deserializeNBT(threads.getCompound(i));
            if (thread.isBusy()) {
                busyThreads.add(thread);
            } else {
                // Idle threads from old saves — discard to save memory.
                // They have no in-progress work.
            }
        }
        totalThreadCount = busyThreads.size();

        int savedRunning = data.getInt("runningThreads");
        // Recalculate runningThreads from actual busy thread count to avoid drift.
        runningThreads = (int) busyThreads.stream().filter(ECOCraftingThread::isBusy).count();
        if (runningThreads != savedRunning && savedRunning >= 0) {
            // Accept the saved value as an upper bound, but clamp to reality.
            runningThreads = Math.max(runningThreads, 0);
        }
    }

    // ── Helpers ──

    public boolean isWorking() {
        return runningThreads > 0;
    }
}
