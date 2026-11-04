package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/** Checks protected production releases without loading or executing their native code. */
class GtoProcessingNativeJarContractTest {
    private static final String PUSH = "(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z";

    @Test void protectedBufferRetainsTheCanonicalPatternMapAndTypedInventories() throws Exception {
        try (var jar = installed("NEOECOAE_GTO_CORE_JAR")) {
            Map<String, ClassNode> classes = new HashMap<>();
            for (var entry : jar.stream().filter(e -> e.getName().endsWith(".class")
                    && e.getName().startsWith("com/gtocore/")).toList()) {
                try (var stream = jar.getInputStream(entry)) {
                    var node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
                    classes.put(node.name, node);
                }
            }
            var buffers = classes.values().stream().filter(node -> node.fields.stream()
                .anyMatch(field -> field.name.equals("circuitInventorySimulated"))
                && node.methods.stream().anyMatch(method -> method.name.equals("pushPattern") && method.desc.equals(PUSH))).toList();
            assertFalse(buffers.isEmpty(), "Installed GTO buffer layout is unavailable");
            for (var buffer : buffers) {
                boolean map = false;
                for (var node = buffer; node != null; node = classes.get(node.superName))
                    map |= node.fields.stream().anyMatch(field -> field.desc.equals("Lcom/google/common/collect/BiMap;")
                        && field.signature != null && field.signature.contains("Lappeng/api/crafting/IPatternDetails;"));
                assertTrue(map, "Canonical routing map is missing from " + buffer.name);
            }
            assertTrue(classes.values().stream().anyMatch(node ->
                node.fields.stream().anyMatch(field -> inventory(field.desc, field.signature, "AEItemKey"))
                && node.fields.stream().anyMatch(field -> inventory(field.desc, field.signature, "AEFluidKey"))
                && node.methods.stream().anyMatch(method -> method.desc.equals(PUSH))),
                "Installed GTO slot no longer has the expected physical item/fluid inventories");
            var wildcard = classes.values().stream().filter(node -> node.fields.stream()
                .anyMatch(field -> field.name.equals("blacklistedItems"))).findFirst().orElseThrow();
            int arrays = 0;
            for (var node = wildcard; node != null; node = classes.get(node.superName)) {
                for (var field : node.fields) {
                    if ((field.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0
                            || !field.desc.startsWith("[Lcom/gtocore/")) continue;
                    var component = classes.get(field.desc.substring(2, field.desc.length() - 1));
                    if (component != null && component.methods.stream().anyMatch(method -> method.desc.equals(PUSH))) arrays++;
                }
            }
            assertEquals(1, arrays, "Wildcard routing must expose exactly one native slot array");
        }
    }

    @Test void installedGtoInventoryAndProgressApisMatchTheAdapter() throws Exception {
        try (var jar = installed("NEOECOAE_GTO_GTM_JAR")) {
            method(read(jar, "com/gregtechceu/gtceu/api/transfer/key/KeyInventory"),
                "insert", "(Lappeng/api/stacks/AEKey;JZ)J");
            var logic = read(jar, "com/gregtechceu/gtceu/api/machine/trait/RecipeLogic");
            method(logic, "getIdleReason", "()Lnet/minecraft/network/chat/Component;");
            method(logic, "getMaxProgress", "()I");
            method(logic, "getProgress", "()I");
            method(logic, "isWorking", "()Z");
            method(logic, "isWaiting", "()Z");
            method(logic, "isSuspend", "()Z");
            var recipe = read(jar, "com/gregtechceu/gtceu/api/recipe/GTRecipe");
            for (String field : new String[]{"itemOutputs", "fluidOutputs", "definition", "parallels"})
                assertTrue(recipe.fields.stream().anyMatch(candidate -> candidate.name.equals(field)), field);
            var contents = read(jar, "com/gregtechceu/gtceu/api/recipe/content/ContentList");
            method(contents, "size", "()I");
            method(contents, "outputKey", "(I)Lappeng/api/stacks/AEKey;");
            assertTrue(read(jar, "com/gregtechceu/gtceu/api/machine/multiblock/part/MultiblockPartMachine")
                .fields.stream().anyMatch(field -> field.name.equals("controllerPositions")
                    && field.desc.equals("Lit/unimi/dsi/fastutil/longs/LongSet;")));
        }
    }

    @Test void installedAe2RetainsTheEmissionAndCpuPacketBoundaries() throws Exception {
        try (var jar = installed("NEOECOAE_GTO_AE2_JAR")) {
            method(read(jar, "appeng/crafting/pattern/AEProcessingPattern"), "pushInputsToExternalInventory",
                "([Lappeng/api/stacks/KeyCounter;Lappeng/api/crafting/IPatternDetails$PatternInputSink;)V");
            var entry = read(jar, "appeng/menu/me/crafting/CraftingStatusMenu$CraftingCpuListEntry");
            method(entry, "writeToPacket", "(Lnet/minecraft/network/FriendlyByteBuf;)V");
            method(entry, "readFromPacket", "(Lnet/minecraft/network/FriendlyByteBuf;)Lappeng/menu/me/crafting/CraftingStatusMenu$CraftingCpuListEntry;");
        }
    }

    private static boolean inventory(String descriptor, String signature, String key) {
        return descriptor.equals("Lcom/gregtechceu/gtceu/api/transfer/key/KeyInventory;")
            && signature != null && signature.contains("Lappeng/api/stacks/" + key + ";");
    }
    private static JarFile installed(String variable) throws Exception {
        var path = System.getenv(variable);
        assumeTrue(path != null && !path.isBlank(), "Supply " + variable + " for the installed-release contract check");
        return new JarFile(path);
    }
    private static ClassNode read(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(name + ".class"); assertNotNull(entry, name);
        try (var stream = jar.getInputStream(entry)) {
            var node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_CODE); return node;
        }
    }
    private static void method(ClassNode type, String name, String descriptor) {
        assertTrue(type.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)),
            type.name + "." + name + descriptor);
    }
}
