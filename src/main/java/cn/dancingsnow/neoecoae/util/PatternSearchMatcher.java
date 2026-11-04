package cn.dancingsnow.neoecoae.util;

/** Literal matching for the workstation and crafting interface search fields. */
public final class PatternSearchMatcher {
    private PatternSearchMatcher() {}
    public static boolean contains(String keywords, String term) { return keywords.contains(term); }
}
