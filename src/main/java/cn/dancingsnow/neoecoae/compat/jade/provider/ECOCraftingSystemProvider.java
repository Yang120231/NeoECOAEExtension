package cn.dancingsnow.neoecoae.compat.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import com.gtocore.integration.jade.provider.RecipeLogicProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOCraftingSystemProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String KEY_RUNNING = "running";
    private static final String KEY_CURRENT_TICKS = "currentTicks";
    private static final String KEY_TOTAL_TICKS = "totalTicks";
    private static final String KEY_AE_ENERGY_PER_TICK = "aeEnergyPerTick";
    private static final String KEY_GT_ENERGY_PER_TICK = "gtEnergyPerTick";
    private static final String KEY_GT_VOLTAGE = "gtVoltage";
    private static final String KEY_TIME_MULTIPLIER = "timeMultiplier";
    private static final String KEY_CHUNK_FORCED = "chunkForced";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_TASK_OUTPUT = "output";
    private static final String KEY_TASK_AMOUNT = "amount";
    private static final String KEY_TASK_STATUS = "status";
    private static final int MAX_VISIBLE_TASKS = 3;
    private static final int MAX_SERIALIZED_TASKS = MAX_VISIBLE_TASKS + 1;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        appendTaskLines(tooltip, data.getList(KEY_TASKS, Tag.TAG_COMPOUND));
        CraftingJadeProgress.appendProgress(tooltip, data, KEY_RUNNING, KEY_CURRENT_TICKS, KEY_TOTAL_TICKS);
        appendRuntimeLines(tooltip, data);
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOCraftingSystemBlockEntity system) {
            ECOCraftingSystemBlockEntity.RuntimeProgressSummary runtime = system.getRuntimeProgressSummary();
            boolean running = runtime.running() || system.isRunning();

            tag.putBoolean(KEY_RUNNING, running);
            tag.putLong(KEY_CURRENT_TICKS, runtime.runningTicks());
            tag.putLong(KEY_TOTAL_TICKS, runtime.totalTicks());
            tag.putLong(KEY_AE_ENERGY_PER_TICK, Math.max(0L, system.getCurrentAeEnergyPerTick()));
            tag.putLong(KEY_GT_ENERGY_PER_TICK, Math.max(0L, system.getCurrentGtEnergyPerTick()));
            tag.putLong(KEY_GT_VOLTAGE, Math.max(0L, system.getWirelessCoverVoltage()));
            tag.putDouble(KEY_TIME_MULTIPLIER, Math.max(0.0D, system.getTimeMultiplier()));
            tag.putBoolean(KEY_CHUNK_FORCED, isChunkForced(system));
            tag.put(KEY_TASKS, writeTasks(system.createCraftingUiState().recipeEntries()));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_system");
    }

    private static ListTag writeTasks(Iterable<NECraftingRecipeUiEntry> entries) {
        ListTag tasks = new ListTag();
        int written = 0;
        for (NECraftingRecipeUiEntry entry : entries) {
            if (written >= MAX_SERIALIZED_TASKS) {
                break;
            }
            if (entry.output().isEmpty()) {
                continue;
            }
            CompoundTag task = new CompoundTag();
            task.put(KEY_TASK_OUTPUT, entry.output().save(new CompoundTag()));
            task.putLong(KEY_TASK_AMOUNT, Math.max(0L, entry.outputAmount()));
            task.putString(KEY_TASK_STATUS, entry.status().name());
            tasks.add(task);
            written++;
        }
        return tasks;
    }

    private static void appendTaskLines(ITooltip tooltip, ListTag tasks) {
        int visible = Math.min(MAX_VISIBLE_TASKS, tasks.size());
        for (int i = 0; i < visible; i++) {
            tooltip.add(taskLine(tooltip, tasks.getCompound(i)));
        }
        if (tasks.size() > MAX_VISIBLE_TASKS) {
            tooltip.add(Component.literal("...").withStyle(ChatFormatting.GRAY));
        }
    }

    private static List<snownee.jade.api.ui.IElement> taskLine(ITooltip tooltip, CompoundTag task) {
        ItemStack output = ItemStack.of(task.getCompound(KEY_TASK_OUTPUT));
        long amount = task.getLong(KEY_TASK_AMOUNT);
        NECraftingRecipeUiEntry.Status status = readStatus(task.getString(KEY_TASK_STATUS));
        boolean running = status == NECraftingRecipeUiEntry.Status.RUNNING;
        MutableComponent prefix = Component.literal(running ? "\u5408\u6210\u4e2d " : "\u6392\u961f\u4e2d ")
                .withStyle(running ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        MutableComponent suffix = output.isEmpty()
                ? Component.empty()
                : output.getHoverName().copy().withStyle(ChatFormatting.GRAY);
        if (amount > 1L) {
            suffix.append(Component.literal(" x" + NELDLibText.compactTaskAmount(amount))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (output.isEmpty()) {
            return List.of(tooltip.getElementHelper().text(prefix));
        }
        return List.of(
                tooltip.getElementHelper().text(prefix),
                tooltip.getElementHelper().smallItem(output.copyWithCount(1)),
                tooltip.getElementHelper().text(Component.literal(" ").append(suffix)));
    }

    private static void appendRuntimeLines(ITooltip tooltip, CompoundTag data) {
        long aeEnergy = data.getLong(KEY_AE_ENERGY_PER_TICK);
        long gtEnergy = data.getLong(KEY_GT_ENERGY_PER_TICK);
        if (aeEnergy > 0L) {
            tooltip.add(Component.literal("AE \u80fd\u8017: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(JadeText.value(JadeText.formatNumber(aeEnergy) + " AE/t", ChatFormatting.AQUA)));
        }
        if (gtEnergy > 0L) {
            List<Component> gtEnergyLines = new ArrayList<>(1);
            long voltage = data.getLong(KEY_GT_VOLTAGE);
            RecipeLogicProvider.getEUtTooltip(gtEnergyLines, gtEnergy, false, voltage > 0L ? voltage : -1L);
            tooltip.addAll(gtEnergyLines);
        }
        tooltip.add(Component.literal("\u603b\u8017\u65f6\u500d\u7387: ")
                .withStyle(ChatFormatting.GRAY)
                .append(JadeText.value(
                        JadeText.formatPercent(data.getDouble(KEY_TIME_MULTIPLIER)), ChatFormatting.AQUA)));
        tooltip.add(
                data.getBoolean(KEY_CHUNK_FORCED)
                        ? Component.literal("\u8be5\u673a\u5668\u6240\u5728\u533a\u5757\u5df2\u5f3a\u5236\u52a0\u8f7d")
                                .withStyle(ChatFormatting.GREEN)
                        : Component.translatable("gtocore.machine.forced_loaded")
                                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static boolean isChunkForced(ECOCraftingSystemBlockEntity system) {
        if (system.getLevel() instanceof ServerLevel serverLevel) {
            long chunkPos = ChunkPos.asLong(system.getBlockPos());
            return serverLevel.getChunkSource().chunkMap.getDistanceManager().shouldForceTicks(chunkPos);
        }
        return false;
    }

    private static NECraftingRecipeUiEntry.Status readStatus(String name) {
        try {
            return NECraftingRecipeUiEntry.Status.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return NECraftingRecipeUiEntry.Status.QUEUED;
        }
    }
}
