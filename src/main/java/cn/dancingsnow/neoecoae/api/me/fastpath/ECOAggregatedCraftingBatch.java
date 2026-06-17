package cn.dancingsnow.neoecoae.api.me.fastpath;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ECOAggregatedCraftingBatch {
    @Nullable private final UUID craftingJobId;

    private final long craftCount;
    private final Map<AEKey, Long> inputTotals;
    private final Map<AEKey, Long> outputTotals;
    private long startedAtTick;
    private long totalTicks;
    private long remainingTicks;
    private ECOCraftingEnergyMode energyMode;
    private ECOCraftingEnergyStatus energyStatus;
    private long energyPerTick;
    private boolean energyProfileAssigned;
    private boolean completed;
    private boolean canceled;

    private ECOAggregatedCraftingBatch(
            @Nullable UUID craftingJobId,
            long craftCount,
            Map<AEKey, Long> inputTotals,
            Map<AEKey, Long> outputTotals,
            long startedAtTick,
            long totalTicks,
            long remainingTicks,
            ECOCraftingEnergyMode energyMode,
            ECOCraftingEnergyStatus energyStatus,
            long energyPerTick,
            boolean energyProfileAssigned,
            boolean completed,
            boolean canceled) {
        this.craftingJobId = craftingJobId;
        this.craftCount = craftCount;
        this.inputTotals = inputTotals;
        this.outputTotals = outputTotals;
        this.startedAtTick = Math.max(0L, startedAtTick);
        this.totalTicks = Math.max(0L, totalTicks);
        this.remainingTicks = completed ? 0L : Math.max(0L, Math.min(this.totalTicks, remainingTicks));
        this.energyMode = energyMode == null ? ECOCraftingEnergyMode.AE : energyMode;
        this.energyStatus = energyStatus == null ? ECOCraftingEnergyStatus.READY : energyStatus;
        this.energyPerTick = Math.max(0L, energyPerTick);
        this.energyProfileAssigned = energyProfileAssigned;
        this.completed = completed;
        this.canceled = canceled;
    }

    @Nullable public static ECOAggregatedCraftingBatch create(ECOBatchCraftingRequest request, long startedAtTick) {
        try {
            Map<AEKey, Long> inputs =
                    toMap(ECOBatchCraftingHelper.multiply(request.inputsPerCraft(), request.batchSize()));
            Map<AEKey, Long> outputs =
                    toMap(ECOBatchCraftingHelper.multiply(request.outputsPerCraft(), request.batchSize()));
            merge(outputs, ECOBatchCraftingHelper.multiply(request.remainingPerCraft(), request.batchSize()));
            if (inputs.isEmpty() || outputs.isEmpty()) {
                return null;
            }
            return new ECOAggregatedCraftingBatch(
                    request.craftingJobId(),
                    request.batchSize(),
                    inputs,
                    outputs,
                    startedAtTick,
                    0L,
                    0L,
                    ECOCraftingEnergyMode.AE,
                    ECOCraftingEnergyStatus.READY,
                    0L,
                    false,
                    false,
                    false);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public long craftCount() {
        return craftCount;
    }

    @Nullable public UUID craftingJobId() {
        return craftingJobId;
    }

    public boolean belongsToJob(UUID jobId) {
        return jobId.equals(craftingJobId);
    }

    public long startedAtTick() {
        return startedAtTick;
    }

    public long totalTicks() {
        return totalTicks;
    }

    public long remainingTicks() {
        return remainingTicks;
    }

    public ECOCraftingEnergyMode energyMode() {
        return energyMode;
    }

    public ECOCraftingEnergyStatus energyStatus() {
        return energyStatus;
    }

    public long energyPerTick() {
        return energyPerTick;
    }

    public boolean energyProfileAssigned() {
        return energyProfileAssigned;
    }

    public boolean completed() {
        return completed;
    }

    public boolean canceled() {
        return canceled;
    }

    public boolean isProcessing() {
        return !canceled && !completed && remainingTicks > 0L;
    }

    public boolean hasTiming() {
        return totalTicks > 0L || remainingTicks > 0L;
    }

    public double progress() {
        return completed ? 1.0D : ECOAggregatedCraftingTiming.progress(totalTicks, remainingTicks);
    }

    public long totalOutputAmount() {
        long total = 0L;
        for (long amount : outputTotals.values()) {
            if (amount > 0L) {
                total = ECOAggregatedCraftingTiming.saturatedAdd(total, amount);
            }
        }
        return total;
    }

    @Nullable public GenericStack primaryOutput() {
        for (Map.Entry<AEKey, Long> entry : outputTotals.entrySet()) {
            if (entry.getValue() > 0L) {
                return new GenericStack(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    public ItemStack primaryOutputStack() {
        GenericStack output = primaryOutput();
        if (output == null || !(output.what() instanceof AEItemKey itemKey)) {
            return ItemStack.EMPTY;
        }
        return itemKey.toStack((int) Math.min(Integer.MAX_VALUE, output.amount()));
    }

    public void assignEnergyProfile(ECOBatchEnergyProfile profile, boolean preserveProgress) {
        if (canceled || completed) {
            return;
        }
        long previousTotal = totalTicks;
        long previousRemaining = remainingTicks;
        energyMode = profile.mode();
        energyStatus = profile.status();
        energyPerTick = profile.energyPerTick();
        energyProfileAssigned = true;
        totalTicks = profile.totalTicks();
        remainingTicks = preserveProgress
                ? ECOAggregatedCraftingTiming.rescaleRemaining(previousTotal, previousRemaining, totalTicks)
                : totalTicks;
    }

    public boolean setEnergyStatus(ECOCraftingEnergyStatus status) {
        ECOCraftingEnergyStatus safeStatus = status == null ? ECOCraftingEnergyStatus.READY : status;
        if (energyStatus == safeStatus) {
            return false;
        }
        energyStatus = safeStatus;
        return true;
    }

    public boolean tick(long elapsedTicks) {
        if (!isProcessing()) {
            return false;
        }
        long previous = remainingTicks;
        remainingTicks = Math.max(0L, remainingTicks - Math.max(1L, elapsedTicks));
        if (remainingTicks == 0L) {
            completed = true;
            inputTotals.clear();
        }
        return previous != remainingTicks;
    }

    public boolean cancel() {
        if (canceled) {
            return false;
        }
        canceled = true;
        return true;
    }

    public boolean recoverToStorage(MEStorage storage, IActionSource source) {
        canceled = true;
        Map<AEKey, Long> recoverable = completed ? outputTotals : inputTotals;
        Iterator<Map.Entry<AEKey, Long>> iterator = recoverable.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<AEKey, Long> entry = iterator.next();
            long inserted = storage.insert(entry.getKey(), entry.getValue(), Actionable.MODULATE, source);
            if (inserted >= entry.getValue()) {
                iterator.remove();
            } else if (inserted > 0L) {
                entry.setValue(entry.getValue() - inserted);
            }
        }
        return recoverable.isEmpty();
    }

    public boolean clearCompletedOutputs() {
        if (!completed || canceled) {
            return false;
        }
        outputTotals.clear();
        return true;
    }

    public CompoundTag writeToTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("craftCount", craftCount);
        tag.putLong("startedAtTick", startedAtTick);
        tag.putLong("totalTicks", totalTicks);
        tag.putLong("remainingTicks", remainingTicks);
        tag.putString("energyMode", energyMode.name());
        tag.putString("energyStatus", energyStatus.name());
        tag.putLong("energyPerTick", energyPerTick);
        tag.putBoolean("energyProfileAssigned", energyProfileAssigned);
        tag.putBoolean("completed", completed);
        tag.putBoolean("canceled", canceled);
        if (craftingJobId != null) {
            tag.putUUID("craftingJobId", craftingJobId);
        }
        tag.put("inputs", writeStacks(inputTotals));
        tag.put("outputs", writeStacks(outputTotals));
        return tag;
    }

    @Nullable public static ECOAggregatedCraftingBatch readFromTag(CompoundTag tag) {
        long craftCount = tag.getLong("craftCount");
        if (craftCount <= 0L) {
            return null;
        }
        Map<AEKey, Long> inputs = readStacks(tag.getList("inputs", Tag.TAG_COMPOUND));
        Map<AEKey, Long> outputs = readStacks(tag.getList("outputs", Tag.TAG_COMPOUND));
        boolean completed = tag.getBoolean("completed");
        if (outputs.isEmpty() || (!completed && inputs.isEmpty())) {
            return null;
        }
        return new ECOAggregatedCraftingBatch(
                tag.hasUUID("craftingJobId") ? tag.getUUID("craftingJobId") : null,
                craftCount,
                inputs,
                outputs,
                tag.getLong("startedAtTick"),
                tag.getLong("totalTicks"),
                tag.getLong("remainingTicks"),
                readEnergyMode(tag.getString("energyMode")),
                readEnergyStatus(tag.getString("energyStatus")),
                tag.getLong("energyPerTick"),
                tag.getBoolean("energyProfileAssigned"),
                completed,
                tag.getBoolean("canceled"));
    }

    private static ECOCraftingEnergyMode readEnergyMode(String name) {
        try {
            return ECOCraftingEnergyMode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return ECOCraftingEnergyMode.AE;
        }
    }

    private static ECOCraftingEnergyStatus readEnergyStatus(String name) {
        try {
            return ECOCraftingEnergyStatus.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return ECOCraftingEnergyStatus.READY;
        }
    }

    private static Map<AEKey, Long> toMap(List<GenericStack> stacks) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        merge(result, stacks);
        return result;
    }

    private static void merge(Map<AEKey, Long> target, List<GenericStack> stacks) {
        for (GenericStack stack : stacks) {
            target.merge(stack.what(), stack.amount(), Math::addExact);
        }
    }

    private static ListTag writeStacks(Map<AEKey, Long> stacks) {
        ListTag list = new ListTag();
        for (Map.Entry<AEKey, Long> entry : stacks.entrySet()) {
            if (entry.getValue() > 0L) {
                list.add(GenericStack.writeTag(new GenericStack(entry.getKey(), entry.getValue())));
            }
        }
        return list;
    }

    private static Map<AEKey, Long> readStacks(ListTag list) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            GenericStack stack = GenericStack.readTag(list.getCompound(i));
            if (stack != null && stack.amount() > 0L) {
                result.merge(stack.what(), stack.amount(), ECOAggregatedCraftingTiming::saturatedAdd);
            }
        }
        return result;
    }
}
