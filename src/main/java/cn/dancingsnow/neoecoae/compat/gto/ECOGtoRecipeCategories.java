package cn.dancingsnow.neoecoae.compat.gto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** GTO replaces category buckets with immutable empty sets after building its client display cache. */
final class ECOGtoRecipeCategories {
    private ECOGtoRecipeCategories() {}

    static <K, V> void prepare(Map<K, Set<V>> categories) {
        for (K category : new ArrayList<>(categories.keySet())) {
            Set<V> mutable = Collections.newSetFromMap(new IdentityHashMap<>());
            mutable.addAll(categories.get(category));
            categories.put(category, mutable);
        }
    }
}
