package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Formats collections in planner diagnostics the way java.util does ({@code {k=v}}, {@code [a]}). Planner maps and
 * sets may be fastutil collections, whose own toString uses {@code {k=>v}} and {@code {a}}; player-visible
 * diagnostic text must not depend on which implementation backs a collection.
 */
public final class PlannerText {
    private PlannerText() {}

    public static String describe(Object value) {
        if (value instanceof Map<?, ?> map) {
            StringJoiner joiner = new StringJoiner(", ", "{", "}");
            for (var entry : map.entrySet()) joiner.add(describe(entry.getKey()) + "=" + describe(entry.getValue()));
            return joiner.toString();
        }
        if (value instanceof Collection<?> collection) {
            StringJoiner joiner = new StringJoiner(", ", "[", "]");
            for (Object element : collection) joiner.add(describe(element));
            return joiner.toString();
        }
        return String.valueOf(value);
    }
}
