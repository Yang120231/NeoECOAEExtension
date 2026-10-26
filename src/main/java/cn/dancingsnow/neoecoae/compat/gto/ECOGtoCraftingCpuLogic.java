package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuFastPath;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuSupport;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.Ae2CpuTimeTrackerAccessor;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/**
 * Keeps GTO's optimized CPU as the authority for its job, inventory and persistence. GTO release class and
 * member names are randomized; resolve the uniquely typed ledger fields once and cache VarHandles instead
 * of binding Mixins to a protected release name or looking up reflection members during ticks.
 */
public final class ECOGtoCraftingCpuLogic extends CraftingCpuLogic {
    private static final ThreadLocal<UUID> ACTIVE_JOB = new ThreadLocal<>();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("neoecoae");
    private static final ClassValue<Layout> LAYOUTS = new ClassValue<>() {
        @Override protected Layout computeValue(Class<?> type) { return new Layout(type); }
    };
    private static final ClassValue<VarHandle> COUNTERS = new ClassValue<>() {
        @Override protected VarHandle computeValue(Class<?> type) { return handle(unique(type, long.class)); }
    };
    private final CraftingCpuLogic delegate;
    private final ECOExternalCpuFastPath fastPath;
    private final NativeJob nativeJob;

    public static UUID currentJob() { return ACTIVE_JOB.get(); }

    public ECOGtoCraftingCpuLogic(CraftingCPUCluster cluster, CraftingCpuLogic delegate) {
        super(cluster);
        this.delegate = delegate;
        fastPath = new ECOExternalCpuFastPath(cluster::markDirty);
        nativeJob = new NativeJob(LAYOUTS.get(delegate.getClass()));
    }

    @Override public ICraftingSubmitResult trySubmitJob(IGrid grid, ICraftingPlan plan, IActionSource source,
            ICraftingRequester requester) {
        if (!ECOExternalCpuSupport.supportsPlan(plan)) return CraftingSubmitResult.NO_CPU_FOUND;
        return delegate.trySubmitJob(grid, plan, source, requester);
    }

    @Override public void tickCraftingLogic(IEnergyService energy, CraftingService crafting) {
        var before = delegate.getLastLink();
        fastPath.refundIdleCredit(energy);
        Object job = nativeJob.layout.job.get(delegate);
        nativeJob.bind(job);
        if (nativeJob.suspended && job != null) return;
        if (cluster.isActive() && job != null && before != null && !before.isCanceled()
                && !nativeJob.neoecoae$suspended()) {
            int dispatched = 0;
            try {
                int budget = cluster.getCoProcessors();
                while (dispatched < budget) {
                    int consumed = fastPath.execute(delegate, nativeJob, delegate.getInventory(), budget - dispatched,
                            crafting, energy, cluster.getLevel());
                    if (consumed == 0) break;
                    dispatched += consumed;
                }
            } catch (RuntimeException failure) {
                nativeJob.neoecoae$suspended(true);
                cluster.markDirty();
                LOGGER.error("Suspended GTO CPU after ECO batch dispatch failure", failure);
                return;
            }
            // A batch consumes the same push budget as native dispatch. Leave mixed native work for the
            // next tick instead of spending the CPU budget a second time inside the GTO engine.
            if (dispatched > 0) return;
        }
        UUID previous = ACTIVE_JOB.get();
        if (before != null) ACTIVE_JOB.set(before.getCraftingID());
        try {
            delegate.tickCraftingLogic(energy, crafting);
        } finally {
            if (previous == null) ACTIVE_JOB.remove(); else ACTIVE_JOB.set(previous);
        }
        completed(before, true);
    }

    @Override public long insert(AEKey key, long amount, Actionable mode) {
        var before = delegate.getLastLink();
        long inserted = delegate.insert(key, amount, mode);
        if (mode == Actionable.MODULATE) completed(before, true);
        return inserted;
    }
    @Override public void cancel() {
        var before = delegate.getLastLink();
        delegate.cancel();
        completed(before, false);
    }
    private void completed(ICraftingLink before, boolean success) {
        if (before == null || delegate.getLastLink() == before) return;
        UUID id = before.getCraftingID();
        if (!success || before.isCanceled()) {
            ECOCraftingJobLifecycle.cancelAndRecover(cluster.getLevel(), id);
            return;
        }
        ECOCraftingJobLifecycle.finish(cluster.getLevel(), id, true);
        var grid = cluster.getGrid();
        if (grid != null) {
            for (var worker : grid.getMachines(
                    cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity.class)) {
                worker.releaseCompletedJobOutputs(id);
            }
        }
    }

    @Override public void readFromNBT(CompoundTag data) {
        delegate.readFromNBT(data);
        fastPath.read(data.getCompound("neoecoaeFastPath"));
        nativeJob.bind(nativeJob.layout.job.get(delegate));
        nativeJob.suspended = data.getBoolean("neoecoaeSuspended");
    }
    @Override public void writeToNBT(CompoundTag data) {
        delegate.writeToNBT(data);
        var ledger = new CompoundTag();
        fastPath.write(ledger);
        data.put("neoecoaeFastPath", ledger);
        data.putBoolean("neoecoaeSuspended", nativeJob.suspended);
    }
    @Override protected void postChange(AEKey key) { cluster.markDirty(); }
    @Override public long getLastModifiedOnTick() { return delegate.getLastModifiedOnTick(); }
    @Override public boolean hasJob() { return delegate.hasJob(); }
    @Override public GenericStack getFinalJobOutput() { return delegate.getFinalJobOutput(); }
    @Override public ElapsedTimeTracker getElapsedTimeTracker() { return delegate.getElapsedTimeTracker(); }
    @Override public ICraftingLink getLastLink() { return delegate.getLastLink(); }
    @Override public ListCraftingInventory getInventory() { return delegate.getInventory(); }
    @Override public void addListener(Consumer<AEKey> listener) { delegate.addListener(listener); }
    @Override public void removeListener(Consumer<AEKey> listener) { delegate.removeListener(listener); }
    @Override public long getStored(AEKey key) { return delegate.getStored(key); }
    @Override public long getWaitingFor(AEKey key) { return delegate.getWaitingFor(key); }
    @Override public void getAllWaitingFor(Set<AEKey> keys) { delegate.getAllWaitingFor(keys); }
    @Override public long getPendingOutputs(AEKey key) { return delegate.getPendingOutputs(key); }
    @Override public void getAllItems(KeyCounter out) { delegate.getAllItems(out); }
    @Override public boolean isCantStoreItems() { return delegate.isCantStoreItems(); }

    private static Field unique(Class<?> owner, Class<?> type) {
        Field found = null;
        for (Field field : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == type) {
                if (found != null) throw new IllegalStateException("Ambiguous GTO ledger field type " + type);
                found = field;
            }
        }
        if (found == null) throw new IllegalStateException("Missing GTO ledger field type " + type);
        return found;
    }
    private static VarHandle handle(Field field) {
        try {
            return MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup()).unreflectVarHandle(field);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access GTO ledger contract", failure);
        }
    }

    private static final class Layout {
        final VarHandle job, tasks, waiting, link, output, tracker, paused;
        Layout(Class<?> cpu) {
            Field nativeJob = null;
            for (Field field : cpu.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                for (Field member : field.getType().getDeclaredFields()) {
                    if (member.getType() == CraftingLink.class) {
                        if (nativeJob != null) throw new IllegalStateException("Ambiguous GTO CPU job");
                        nativeJob = field;
                        break;
                    }
                }
            }
            if (nativeJob == null) throw new IllegalStateException("GTO CPU job contract is unavailable");
            job = handle(nativeJob);
            Class<?> type = nativeJob.getType();
            tasks = handle(unique(type, it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap.class));
            waiting = handle(unique(type, ListCraftingInventory.class));
            link = handle(unique(type, CraftingLink.class));
            output = handle(unique(type, GenericStack.class));
            tracker = handle(unique(type, ElapsedTimeTracker.class));
            paused = pauseHandle(type);
        }
    }

    private static VarHandle pauseHandle(Class<?> type) {
        String path = "/" + type.getName().replace('.', '/') + ".class";
        try (var input = type.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing GTO job bytecode");
            var node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(input).accept(node, org.objectweb.asm.ClassReader.SKIP_DEBUG);
            for (var method : node.methods) {
                for (var instruction : method.instructions) {
                    if (!(instruction instanceof org.objectweb.asm.tree.LdcInsnNode constant)
                            || !"paused".equals(constant.cst)) continue;
                    for (var next = instruction.getNext(); next != null; next = next.getNext()) {
                        if (next instanceof org.objectweb.asm.tree.MethodInsnNode) break;
                        if (next instanceof org.objectweb.asm.tree.FieldInsnNode field
                                && field.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD && field.desc.equals("Z")) {
                            return handle(type.getDeclaredField(field.name));
                        }
                    }
                }
            }
            throw new IllegalStateException("GTO persisted pause field is unavailable");
        } catch (java.io.IOException | NoSuchFieldException failure) {
            throw new IllegalStateException("Cannot bind GTO persisted pause field", failure);
        }
    }
    private static final class NativeJob implements ECOExternalCpuJob {
        final Layout layout;
        Object job;
        boolean suspended;
        NativeJob(Layout layout) { this.layout = layout; }
        void bind(Object value) { if (job != value) suspended = false; job = value; }
        @Override @SuppressWarnings("unchecked") public Map<IPatternDetails, ?> neoecoae$tasks() {
            return (Map<IPatternDetails, ?>) layout.tasks.get(job);
        }
        @Override public ListCraftingInventory neoecoae$waitingFor() { return (ListCraftingInventory) layout.waiting.get(job); }
        @Override public CraftingLink neoecoae$link() { return (CraftingLink) layout.link.get(job); }
        @Override public GenericStack neoecoae$finalOutput() { return (GenericStack) layout.output.get(job); }
        @Override public boolean neoecoae$suspended() { return suspended || (boolean) layout.paused.get(job); }
        @Override public void neoecoae$suspended(boolean value) { suspended = value; }
        @Override public void neoecoae$addRemainderItems(long amount, AEKeyType type) {
            ((Ae2CpuTimeTrackerAccessor) layout.tracker.get(job)).neoecoae$addMaxItems(amount, type);
        }
        @Override public long neoecoae$taskValue(Object task) { return (long) COUNTERS.get(task.getClass()).get(task); }
        @Override public void neoecoae$taskValue(Object task, long value) { COUNTERS.get(task.getClass()).set(task, value); }
    }
}
