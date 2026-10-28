package cn.dancingsnow.neoecoae.crafting.planner;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import java.util.Map;
import java.util.function.ObjLongConsumer;

/**
 * Amount reads and iteration over {@code Map<K, Long>} without boxing when the map is a fastutil map.
 * Planner maps never store null amounts, so a missing key and zero are read the same way callers did with
 * {@code getOrDefault(key, 0L)}; iteration keeps the map's own order.
 */
public final class PlannerLongMaps {
    private PlannerLongMaps() {}

    @SuppressWarnings("unchecked")
    public static <K> long amount(Map<K, Long> map, K key) {
        if (map instanceof Object2LongMap<?> fast) return ((Object2LongMap<K>) fast).getOrDefault(key, 0L);
        Long value = map.get(key);
        return value == null ? 0L : value;
    }

    @SuppressWarnings("unchecked")
    public static <K> void forEach(Map<K, Long> map, ObjLongConsumer<K> action) {
        if (map instanceof Object2LongMap<?> fast) {
            for (var entry : Object2LongMaps.fastIterable((Object2LongMap<K>) fast)) {
                action.accept(entry.getKey(), entry.getLongValue());
            }
            return;
        }
        for (var entry : map.entrySet()) {
            Long value = entry.getValue();
            if (value != null) action.accept(entry.getKey(), value);
        }
    }
}
