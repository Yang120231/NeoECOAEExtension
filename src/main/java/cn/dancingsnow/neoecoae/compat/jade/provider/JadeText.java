package cn.dancingsnow.neoecoae.compat.jade.provider;

import cn.dancingsnow.neoecoae.util.NETextFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class JadeText {
    private static final DecimalFormat PERCENT_FORMAT =
            new DecimalFormat("0.##%", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT =
            ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.US));

    private JadeText() {}

    static String formatNumber(long value) {
        return NUMBER_FORMAT.get().format(value);
    }

    static String formatPercent(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return "0%";
        }
        return PERCENT_FORMAT.format(ratio);
    }

    static Component threadLine(long used, long total) {
        return translated(
                "jade.neoecoae.computation.thread_usage",
                value(formatNumber(used), ChatFormatting.WHITE),
                value(formatNumber(total), ChatFormatting.WHITE));
    }

    static Component storageLine(long used, long total) {
        return translated(
                "jade.neoecoae.computation.storage_usage",
                value(formatStorageNumber(used), ChatFormatting.AQUA),
                value(formatStorageNumber(total), ChatFormatting.AQUA));
    }

    static String formatStorageNumber(long value) {
        return value == Long.MAX_VALUE ? NETextFormat.COMPUTATION_INFINITE_STORAGE_DISPLAY : formatNumber(value);
    }

    static Component energyLine(long energyPerTick) {
        return translated(
                "jade.neoecoae.crafting.energy_usage",
                value(formatNumber(Math.max(0L, energyPerTick)), ChatFormatting.AQUA));
    }

    static Component aeEnergyLine(long energyPerTick) {
        return translated(
                "jade.neoecoae.crafting.ae_energy_usage",
                value(formatNumber(Math.max(0L, energyPerTick)), ChatFormatting.AQUA));
    }

    static Component gtEnergyLine(long energyPerTick) {
        return translated(
                "jade.neoecoae.crafting.gt_energy_usage",
                value(formatNumber(Math.max(0L, energyPerTick)), ChatFormatting.AQUA));
    }

    static Component timeMultiplierLine(double multiplier) {
        return translated(
                "jade.neoecoae.crafting.time_multiplier", value(formatPercent(multiplier), ChatFormatting.AQUA));
    }

    static Component overclockLine(int effective, int theoretical) {
        return translated(
                "jade.neoecoae.crafting.overclock_ratio",
                value(Integer.toString(Math.max(0, effective)), ChatFormatting.AQUA),
                value(Integer.toString(Math.max(0, theoretical)), ChatFormatting.AQUA));
    }

    static Component parallelRecipesLine(long recipes) {
        return translated(
                "jade.neoecoae.crafting.parallel_recipes",
                value(formatNumber(Math.max(0L, recipes)), ChatFormatting.AQUA));
    }

    static Component workingCraftsLine(long recipes) {
        return translated(
                "jade.neoecoae.crafting.working_crafts",
                value(formatNumber(Math.max(0L, recipes)), ChatFormatting.AQUA));
    }

    static Component formedLine(boolean formed) {
        return translated(
                "jade.neoecoae.formed",
                Component.translatable(formed ? "jade.neoecoae.yes" : "jade.neoecoae.no")
                        .withStyle(formed ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    static Component onlineLine(boolean online) {
        return translated(
                "jade.neoecoae.online",
                Component.translatable(online ? "jade.neoecoae.yes" : "jade.neoecoae.no")
                        .withStyle(online ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    static Component runningLine(boolean running) {
        return translated(
                "jade.neoecoae.running",
                Component.translatable(running ? "jade.neoecoae.yes" : "jade.neoecoae.no")
                        .withStyle(running ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }

    static Component recipesPerOperationLine(long recipes) {
        return Component.literal("\u6bcf\u6b21\u5904\u7406\u914d\u65b9\u6570\u91cf: ")
                .withStyle(ChatFormatting.GRAY)
                .append(value(formatNumber(Math.max(0L, recipes)), ChatFormatting.AQUA));
    }

    static Component progressValueLine(long progress) {
        return translated(
                "jade.neoecoae.crafting.progress_value",
                value(formatNumber(clampProgress(progress)), ChatFormatting.AQUA));
    }

    static Component averageProgressLine(long progress) {
        return translated(
                "jade.neoecoae.crafting.avg_progress",
                value(formatNumber(clampProgress(progress)), ChatFormatting.AQUA));
    }

    static Component speedLine(long progressPerTick) {
        return translated(
                "jade.neoecoae.crafting.speed",
                value(formatNumber(Math.max(0L, progressPerTick)), ChatFormatting.AQUA));
    }

    static Component durationLine(long ticks) {
        long safeTicks = Math.max(0L, ticks);
        return translated(
                "jade.neoecoae.crafting.duration",
                value(formatNumber(safeTicks), ChatFormatting.AQUA),
                value(String.format(Locale.ROOT, "%.1f", safeTicks / 20.0D), ChatFormatting.AQUA));
    }

    static Component batchSlotsLine(long occupied, long total) {
        return translated(
                "jade.neoecoae.crafting.batch_slots",
                value(
                        formatNumber(Math.max(0L, occupied)) + "/" + formatNumber(Math.max(0L, total)),
                        ChatFormatting.AQUA));
    }

    private static long clampProgress(long progress) {
        return Math.max(0L, Math.min(100L, progress));
    }

    static MutableComponent translated(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }

    static MutableComponent value(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }
}
