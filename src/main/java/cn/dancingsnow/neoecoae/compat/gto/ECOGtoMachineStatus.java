package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Read-only GTO machine observation. No inventories are probed or chunks loaded. */
public final class ECOGtoMachineStatus {
    private static final ClassValue<Map<String, Optional<Member>>> MEMBERS = new ClassValue<>() {
        @Override protected Map<String, Optional<Member>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private ECOGtoMachineStatus() {}
    private static final cn.dancingsnow.neoecoae.crafting.display.processing.ECOFxProcessingStatus FX_STATUS =
        new cn.dancingsnow.neoecoae.crafting.display.processing.ECOFxProcessingStatus();

    public static List<Target> targets(ICraftingProvider provider) {
        Object current = provider;
        for (int i = 0; current != null && i < 8; i++) {
            if (current instanceof ECOProcessingTargetProvider traced) {
                Target target = traced.neoecoae$getProcessingTarget();
                return target == null ? List.of() : List.of(target);
            }
            if (ECOGtoProcessingDispatch.isBuffer(current)) {
                Object world = value(current, "getLevel");
                Object position = value(current, "getPos", "getBlockPos");
                return world instanceof Level level && position instanceof BlockPos pos
                    ? List.of(new Target(level.dimension().location(), pos)) : List.of();
            }
            Object next = value(current, "getLogic");
            if (next == current) break;
            current = next;
        }
        return List.of();
    }

    /** Incremental traversal. Completion has no machine-count limit; the caller budgets each step. */
    public static final class Scan {
        private final ArrayDeque<Target> pending = new ArrayDeque<>();
        private final HashSet<Target> visited = new HashSet<>();
        private final java.util.LinkedHashMap<String, Machine> machines = new java.util.LinkedHashMap<>();
        private final LinkedHashSet<Target> machineTargets = new LinkedHashSet<>();
        private final AEKey output;
        private final java.util.UUID jobId;

        public Scan(Iterable<Target> roots, AEKey output) {
            this(roots, output, null);
        }

        public Scan(Iterable<Target> roots, AEKey output, java.util.UUID jobId) {
            for (var root : roots) pending.add(root);
            this.output = output;
            this.jobId = jobId;
        }

        public boolean complete() { return pending.isEmpty(); }
        public List<Machine> machines() { return new ArrayList<>(machines.values()); }
        public List<Target> machineTargets() { return List.copyOf(machineTargets); }
        public void addRoot(Target target) { if (!visited.contains(target)) pending.addLast(target); }

        /** One step visits at most one coordinate, including duplicate and unavailable coordinates. */
        public void step(Level context) {
            if (pending.isEmpty() || context == null || context.isClientSide || context.getServer() == null) return;
            Target target = pending.removeFirst();
            if (!visited.add(target)) return;
            Level level = context.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, target.dimension()));
            if (level == null || !level.hasChunkAt(target.position())) {
                machines.put(location(target), unavailable(target, "unloaded"));
                return;
            }
            var be = level.getBlockEntity(target.position());
            if (be == null || be.isRemoved()) return;
            if (be instanceof cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity workstation) {
                machineTargets.add(target);
                machines.put(location(target), workstation.getProcessingStatus(output));
                return;
            }
            if (FX_STATUS.supports(be)) {
                var observation = FX_STATUS.read(be,
                    new cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusReader.Context(output, jobId));
                if (observation.sample() != null) {
                    machineTargets.add(target);
                    machines.put(location(target), observation.sample());
                }
                return;
            }
            Object machine = value(be, "getMetaMachine");
            if (machine == null) return;
            Object logic = value(machine, "getRecipeLogic");
            if (logic != null) {
                machineTargets.add(target);
                machines.put(location(target), sampleMachine(logic, be.getBlockState().getBlock().getName(), location(target), output));
                return;
            }
            // Resolving getters may prune proxies or resolve unloaded controllers. Read only stored positions.
            addPositions(pending, target, value(machine, "controllerPositions"));
            if (ECOGtoProcessingDispatch.isBuffer(machine)) addPositions(pending, target, value(machine, "proxies"));
            addPositions(pending, target, value(machine, "bufferPos"));
        }
    }

    /** Tests and adapters can inspect the same version-tolerant recipe-logic contract. */
    static Machine sampleMachine(Object logic, Component name, String location, AEKey output) {
        Object recipe = value(logic, "getLastRecipe", "lastRecipe");
        boolean matches = recipe != null && produces(recipe, output);
        int duration = matches ? positiveInt(value(logic, "getMaxProgress", "getDuration", "duration")) : 0;
        int progress = duration > 0 ? positiveInt(value(logic, "getProgress", "progress")) : 0;
        boolean paused = Boolean.TRUE.equals(value(logic, "isSuspend"));
        boolean waiting = Boolean.TRUE.equals(value(logic, "isWaiting"));
        boolean working = Boolean.TRUE.equals(value(logic, "isWorking"));
        Component reason = paused || waiting || !working ? failureReason(logic) : Component.empty();
        State state = paused ? State.PAUSED
            : waiting || (!working && !reason.getString().isEmpty()) ? State.WAITING
            : matches && working ? State.RUNNING : State.QUEUED;
        if (state == State.QUEUED || (!working && !waiting && !paused)) { duration = 0; progress = 0; }
        long parallel = duration > 0 ? positiveLong(value(recipe, "parallels")) : 0;
        Object id = duration > 0 ? value(value(recipe, "definition"), "id") : null;
        return new Machine(name, location, id == null ? "" : id.toString(), progress, duration,
            parallel, state, reason);
    }

    /** Observe native failures even before a recipe has started; never retry recipes or infer energy from WAITING. */
    private static Component failureReason(Object logic) {
        var reasons = new LinkedHashMap<String, Component>();
        addReason(reasons, component(value(logic, "getIdleReason")));
        Object machine = value(logic, "getMachine", "machine");
        if (Boolean.FALSE.equals(value(machine, "isFormed"))) {
            addReason(reasons, Component.translatable("gui.neoecoae.processing.unformed"));
        }
        var combined = Component.empty();
        for (var reason : reasons.values()) {
            if (!combined.getString().isEmpty()) combined.append("；");
            combined.append(reason.copy());
        }
        return combined;
    }

    private static void addReason(Map<String, Component> reasons, Component reason) {
        if (!reason.getString().isBlank()) reasons.putIfAbsent(reason.getString(), reason);
    }

    private static boolean produces(Object recipe, AEKey output) {
        if (!(recipe instanceof GTRecipe nativeRecipe)) return false;
        if (output instanceof AEItemKey item) {
            return nativeRecipe.itemOutputs.stream()
                .anyMatch(content -> content.amount > 0 && content.inner.testAeKay(item));
        }
        if (output instanceof AEFluidKey fluid) {
            return nativeRecipe.fluidOutputs.stream()
                .anyMatch(content -> content.amount > 0 && content.inner.testAeKay(fluid));
        }
        return false;
    }

    private static void addPositions(ArrayDeque<Target> pending, Target parent, Object positions) {
        if (positions instanceof BlockPos pos) pending.addLast(new Target(parent.dimension(), pos));
        else if (positions instanceof Long pos) pending.addLast(new Target(parent.dimension(), BlockPos.of(pos)));
        else if (positions instanceof Iterable<?> list) {
            for (Object entry : list) {
                addPositions(pending, parent, entry);
            }
        }
    }

    private static String location(Target target) {
        return target.dimension() + " [" + target.position().toShortString() + "]";
    }

    private static Machine unavailable(Target target, String reason) {
        return new Machine(Component.translatable("gui.neoecoae.processing.machine"), location(target), "", 0, 0, 0,
            State.UNAVAILABLE, Component.translatable("gui.neoecoae.processing." + reason));
    }

    private static Component component(Object value) {
        if (value instanceof Optional<?> optional) value = optional.orElse(null);
        return value instanceof Component component ? component : Component.empty();
    }

    private static int positiveInt(Object value) { return (int) Math.min(Integer.MAX_VALUE, positiveLong(value)); }
    private static long positiveLong(Object value) { return value instanceof Number number ? Math.max(0, number.longValue()) : 0; }

    private static Object value(Object object, String... names) {
        if (object == null) return null;
        try {
            var members = MEMBERS.get(object.getClass());
            for (String name : names) {
                var member = members.computeIfAbsent(name, key -> member(object.getClass(), key));
                if (member.isPresent()) {
                    Object result = member.get().get(object);
                    if (result != null) return result;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError incompatible) {
            // Status observation must never affect dispatch or output accounting.
        }
        return null;
    }

    private static Optional<Member> member(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            if (method.trySetAccessible()) return Optional.of(method::invoke);
        } catch (NoSuchMethodException ignored) {}
        for (var current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (field.trySetAccessible()) return Optional.of(field::get);
            } catch (NoSuchFieldException ignored) {}
        }
        return Optional.empty();
    }

    @FunctionalInterface private interface Member {
        Object get(Object object) throws ReflectiveOperationException;
    }
}
