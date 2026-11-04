package cn.dancingsnow.neoecoae.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PatternSearchMatcherTest {
    @Test void matchesLiteralItemAndFluidNames() {
        assertTrue(PatternSearchMatcher.contains("iron ingot 铁锭", "iron"));
        assertTrue(PatternSearchMatcher.contains("iron ingot 铁锭", "铁锭"));
        assertTrue(PatternSearchMatcher.contains("liquid water 液态水", "液态水"));
        assertFalse(PatternSearchMatcher.contains("iron ingot 铁锭", "tieding"));
        assertFalse(PatternSearchMatcher.contains("iron ingot 铁锭", "gear"));
    }
}
