package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import appeng.crafting.execution.CraftingCpuLogic;
import cn.dancingsnow.neoecoae.platform.mixin.GtoCpuLogicSelector;
import cn.dancingsnow.neoecoae.platform.mixin.RouteGtoCpuLogic;
import cn.dancingsnow.neoecoae.mixins.ae2.menu.GtoCraftingCpuMenuMixin;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

class GtoCraftingCpuMenuCompatibilityTest {
    @Test
    void unwrapPreservesTheNativeCpuAndOtherLogic() {
        var delegate = mock(CraftingCpuLogic.class);
        var wrapper = mock(ECOGtoCraftingCpuLogic.class);
        when(wrapper.nativeLogic()).thenReturn(delegate);
        assertSame(delegate, ECOGtoCraftingCpuLogic.unwrap(wrapper));
        assertSame(delegate, ECOGtoCraftingCpuLogic.unwrap(delegate));
    }

    @Test
    void mergedCallbackNamesAndRequiredInjectionCountArePreserved() throws Exception {
        var merged = new MethodNode(Opcodes.ACC_PRIVATE, "handler$abc123$gto$onBroadcastChanges",
            "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
        merged.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
            "appeng/me/cluster/implementations/CraftingCPUCluster", "craftingLogic",
            "Lappeng/crafting/execution/CraftingCpuLogic;"));
        assertTrue(GtoCpuLogicSelector.selects(merged));
        merged.name = "unrelatedAddonMethod";
        assertFalse(GtoCpuLogicSelector.selects(merged));
        var handler = GtoCraftingCpuMenuMixin.class.getDeclaredMethod("neoecoae$gtoNativeCpu",
            appeng.me.cluster.implementations.CraftingCPUCluster.class);
        var route = handler.getAnnotation(RouteGtoCpuLogic.class);
        assertNotNull(route);
        assertEquals(4, route.require());
        assertEquals(4, route.allow());
        assertEquals(Opcodes.GETFIELD, route.at().opcode());
        assertEquals("Lappeng/me/cluster/implementations/CraftingCPUCluster;craftingLogic:"
            + "Lappeng/crafting/execution/CraftingCpuLogic;", route.at().target());
    }

    @Test
    void installedGtoMenuRoutesEveryNativeCpuCast() throws Exception {
        String path = System.getenv("NEOECOAE_GTO_CORE_JAR");
        assumeTrue(path != null && !path.isBlank(), "Set NEOECOAE_GTO_CORE_JAR to the installed GTO Core JAR");
        try (var jar = new JarFile(Path.of(path).toFile())) {
            var entry = jar.getJarEntry("com/gtocore/mixin/ae2/menu/CraftingCPUMenuMixin.class");
            assertNotNull(entry);
            var node = new ClassNode();
            try (var input = jar.getInputStream(entry)) {
                new ClassReader(input).accept(node, 0);
            }
            Set<String> methods = new HashSet<>();
            int casts = 0;
            for (var method : node.methods) {
                for (var instruction : method.instructions) {
                    if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
                            || !field.owner.equals("appeng/me/cluster/implementations/CraftingCPUCluster")
                            || !field.name.equals("craftingLogic")) continue;
                    var next = instruction.getNext();
                    while (next != null && next.getOpcode() < 0) next = next.getNext();
                    if (!(next instanceof TypeInsnNode cast) || cast.getOpcode() != Opcodes.CHECKCAST
                            || !cast.desc.startsWith("com/gtocore/")) continue;
                    assertTrue(GtoCpuLogicSelector.selects(method), method.name);
                    methods.add(method.name);
                    casts++;
                }
            }
            assertEquals(4, casts);
            assertEquals(Set.of("gto$requestPendingBlocks", "gto$onBroadcastChanges",
                "gto$isPaused", "gto$setPaused"), methods);
        }
    }
}
