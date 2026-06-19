package cn.dancingsnow.neoecoae.gui.ldlib.support;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class NELDLibText {
    public static final String INFINITE = "\u221e";
    public static final int TYPE_COUNT_COLOR = 0x54FCFC;

    private static final long BYTES_IN_K = 1024L;
    private static final long BYTES_IN_M = BYTES_IN_K * 1024L;
    private static final long BYTES_IN_G = BYTES_IN_M * 1024L;
    private static final long BYTES_IN_T = BYTES_IN_G * 1024L;
    private static final long BYTES_IN_P = BYTES_IN_T * 1024L;

    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT =
            ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.US));
    private static final ThreadLocal<DecimalFormat> COMPACT_DECIMAL =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> PERCENT_DECIMAL =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US)));

    private NELDLibText() {}

    public static String number(long value) {
        return NUMBER_FORMAT.get().format(value);
    }

    public static String number(int value) {
        return number((long) value);
    }

    public static String typeCount(long value) {
        return value == Long.MAX_VALUE ? INFINITE : number(Math.max(0L, value));
    }

    public static Component typesUsedComponent(long usedTypes) {
        return typesUsedComponent(typeCount(usedTypes));
    }

    public static Component typesUsedComponent(String usedTypesText) {
        return Component.literal(usedTypesText)
                .withStyle(style -> style.withColor(TYPE_COUNT_COLOR))
                .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("gui.neoecoae.common.types").withStyle(ChatFormatting.GRAY));
    }

    public static String percent(double value) {
        if (!Double.isFinite(value)) {
            return "N/A";
        }
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return PERCENT_DECIMAL.get().format(clamped * 100.0D) + "%";
    }

    public static String percentOrNA(long used, long max) {
        return max <= 0 ? "N/A" : percent((double) Math.max(0L, used) / (double) max);
    }

    public static String usedTotal(long used, long max) {
        return number(Math.max(0L, used)) + " / " + number(Math.max(0L, max));
    }

    public static String storageBytes(long value) {
        if (value == Long.MAX_VALUE) {
            return INFINITE;
        }
        long safe = Math.max(0L, value);
        if (safe < BYTES_IN_G) {
            return number(safe);
        }

        long unit = BYTES_IN_G;
        String suffix = "G";
        if (safe >= BYTES_IN_P) {
            unit = BYTES_IN_P;
            suffix = "P";
        } else if (safe >= BYTES_IN_T) {
            unit = BYTES_IN_T;
            suffix = "T";
        }
        return COMPACT_DECIMAL.get().format((double) safe / (double) unit) + suffix;
    }

    public static String compactDecimal(long value, long unit, String suffix) {
        double scaled = (double) Math.max(0L, value) / (double) unit;
        if (scaled >= 100.0D || Math.abs(scaled - Math.rint(scaled)) < 0.05D) {
            return String.format(Locale.US, "%.0f%s", scaled, suffix);
        }
        return String.format(Locale.US, "%.1f%s", scaled, suffix);
    }

    public static String compactTaskAmount(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) {
            return Long.toString(safe);
        }
        if (safe < 1_000_000L) {
            return compactDecimal(safe, 1_000L, "K");
        }
        if (safe < 1_000_000_000L) {
            return compactDecimal(safe, 1_000_000L, "M");
        }
        if (safe < 1_000_000_000_000L) {
            return compactDecimal(safe, 1_000_000_000L, "G");
        }
        return compactDecimal(safe, 1_000_000_000_000L, "T");
    }

    public static String compactCount(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) {
            return Long.toString(safe);
        }
        if (safe < 1_000_000L) {
            return (safe / 1_000L) + "K";
        }
        if (safe < 1_000_000_000L) {
            return (safe / 1_000_000L) + "M";
        }
        return (safe / 1_000_000_000L) + "B";
    }
}
