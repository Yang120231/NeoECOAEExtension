package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ECOGtoFrozenCategoryTest {
    @Test void frozenGtoDisplayBucketsMustBeRestoredBeforeSavingALateRecipe() {
        Map<String, Set<Object>> categories = new HashMap<>();
        categories.put("assembly_line", Collections.emptySet());
        Object definition = new Object();
        assertThrows(UnsupportedOperationException.class, () -> categories.get("assembly_line").add(definition));
        ECOGtoRecipeCategories.prepare(categories);
        assertDoesNotThrow(() -> categories.get("assembly_line").add(definition));
        assertTrue(categories.get("assembly_line").contains(definition));
    }

    @Test void restoringCategoriesPreservesExistingIdentitiesAndDoesNotMutateTheCachedSet() {
        var first = new String("same");
        var second = new String("same");
        Set<Object> frozen = Collections.singleton(first);
        Map<String, Set<Object>> categories = new HashMap<>(Map.of("assembler", frozen));
        ECOGtoRecipeCategories.prepare(categories);
        assertTrue(categories.get("assembler").add(second));
        assertEquals(2, categories.get("assembler").size());
        assertEquals(1, frozen.size());
        ECOGtoRecipeCategories.prepare(categories);
        assertEquals(2, categories.get("assembler").size());
    }
}
