package cn.dancingsnow.neoecoae.compat.jade.provider;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.ITooltip;
import snownee.jade.api.ui.BoxStyle;

final class CraftingJadeProgress {
    private static final int GTO_PROGRESS_BLUE = 0xFF1E8CFF;
    private static final int GTO_PROGRESS_BORDER = 0xFF888888;

    private CraftingJadeProgress() {}

    static void appendProgress(
            ITooltip tooltip, CompoundTag data, String runningKey, String currentKey, String totalKey) {
        boolean running = data.getBoolean(runningKey);
        long currentTicks = data.getLong(currentKey);
        long totalTicks = data.getLong(totalKey);
        if (!running || totalTicks <= 0L) {
            return;
        }

        tooltip.add(tooltip.getElementHelper()
                .progress(
                        progressRatio(currentTicks, totalTicks),
                        Component.literal(formatDurationText(currentTicks) + " / " + formatDurationText(totalTicks)),
                        tooltip.getElementHelper()
                                .progressStyle()
                                .color(GTO_PROGRESS_BLUE, GTO_PROGRESS_BLUE)
                                .textColor(0xFFFFFFFF),
                        progressBoxStyle(),
                        true));
    }

    private static BoxStyle progressBoxStyle() {
        BoxStyle style = new BoxStyle();
        style.borderWidth = BoxStyle.DEFAULT.borderWidth;
        style.borderColor = GTO_PROGRESS_BORDER;
        style.roundCorner = BoxStyle.DEFAULT.roundCorner;
        style.bgColor = BoxStyle.DEFAULT.bgColor;
        return style;
    }

    private static float progressRatio(long currentTicks, long totalTicks) {
        if (totalTicks <= 0L) {
            return 0.0F;
        }
        float ratio = (float) Math.max(0.0D, Math.min(1.0D, currentTicks / (double) totalTicks));
        return currentTicks > 0L ? Math.max(0.01F, ratio) : 0.0F;
    }

    private static String formatDurationText(long ticks) {
        long safeTicks = Math.max(0L, ticks);
        if (safeTicks < 20L) {
            return JadeText.formatNumber(safeTicks) + "t";
        }
        return String.format(java.util.Locale.ROOT, "%.1fs", safeTicks / 20.0D);
    }
}
