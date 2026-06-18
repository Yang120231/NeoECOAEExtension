package cn.dancingsnow.neoecoae.compat.gto;

import cn.dancingsnow.neoecoae.api.me.fastpath.ECOAggregatedCraftingTiming;
import cn.dancingsnow.neoecoae.config.NEConfig;
import java.lang.reflect.Field;

public final class GTOConfigCompat {
    private static final String CONFIG_CLASS = "com.gtocore.config.GTOConfig";

    private GTOConfigCompat() {}

    public static int getBatchProcessingMaxDurationTicks() {
        return sanitizeDurationTicks(readGtoBatchProcessingMaxDurationTicks());
    }

    public static long limitBatchProcessingDurationTicks(long requestedTicks) {
        return requestedTicks <= 0L ? 0L : Math.min(requestedTicks, getBatchProcessingMaxDurationTicks());
    }

    private static int readGtoBatchProcessingMaxDurationTicks() {
        try {
            Class<?> configClass = Class.forName(CONFIG_CLASS);
            Object instance = publicField(configClass, "INSTANCE").get(null);
            Object gamePlay = publicField(configClass, "gamePlay").get(instance);
            return publicField(gamePlay.getClass(), "batchProcessingMaxDuration")
                    .getInt(gamePlay);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return NEConfig.getBatchProcessingMaxDurationTicks();
        }
    }

    private static Field publicField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getField(name);
        field.setAccessible(true);
        return field;
    }

    private static int sanitizeDurationTicks(int ticks) {
        return Math.max(ECOAggregatedCraftingTiming.MINIMUM_DURATION_TICKS, ticks);
    }
}
