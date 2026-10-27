package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.selectors.ElementNode;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector;
import org.spongepowered.asm.mixin.injection.selectors.TargetSelector;

class GtoCraftConfirmMixinCompatibilityTest {
    private static final String MENU = "appeng/menu/me/crafting/CraftConfirmMenu";
    private static final String MIXIN = "cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftConfirmMenuMixin";

    @Test
    void pinnedAe2HasExactlyOneConfirmedSubmissionHook() throws Exception {
        verifySubmissionHook(readResource(MENU));
    }

    @Test
    void submissionStillMatchesWhenNoArgumentEntryDelegatesToBooleanOverload() throws Exception {
        var menu = readResource(MENU);
        // Model the GTO .40 layout using the real pinned submitJob instruction and descriptor.
        var submission = menu.methods.stream().filter(method -> method.name.equals("startJob"))
            .findFirst().orElseThrow();
        submission.desc = "(Z)V";
        var delegate = new MethodNode(Opcodes.ACC_PUBLIC, "startJob", "()V", null, null);
        delegate.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MENU, "startJob", "(Z)V", false));
        menu.methods.add(0, delegate);
        verifySubmissionHook(menu);
    }

    @Test
    void installedGtoAe2HasExactlyOneConfirmedSubmissionHook() throws Exception {
        String path = System.getenv("NEOECOAE_GTO_AE2_JAR");
        assumeTrue(path != null && !path.isBlank(), "Set NEOECOAE_GTO_AE2_JAR to the installed AE2 JAR");
        try (var jar = new JarFile(Path.of(path).toFile());
                var input = jar.getInputStream(jar.getJarEntry(MENU + ".class"))) {
            verifySubmissionHook(read(input));
        }
    }

    private static void verifySubmissionHook(ClassNode menu) throws Exception {
        var mixin = readResource(MIXIN);
        var handler = mixin.methods.stream().filter(method -> method.name.equals("submitConfirmedCyclePlan"))
            .findFirst().orElseThrow();
        var annotations = new ArrayList<AnnotationNode>();
        if (handler.visibleAnnotations != null) annotations.addAll(handler.visibleAnnotations);
        if (handler.invisibleAnnotations != null) annotations.addAll(handler.invisibleAnnotations);
        var group = annotations.stream().filter(annotation -> annotation.desc.endsWith("/Group;"))
            .findFirst().orElseThrow();
        assertEquals(1, value(group, "min"), "Missing submission routing must remain fatal");
        assertEquals(1, value(group, "max"), "A submission must not be wrapped twice");
        var wrapper = annotations.stream().filter(annotation -> annotation.desc.endsWith("/WrapOperation;"))
            .findFirst().orElseThrow();
        var points = (List<?>) value(wrapper, "at");
        String target = (String) value((AnnotationNode) points.get(0), "target");
        long matches = 0;
        for (var text : (List<?>) value(wrapper, "method")) {
            var selector = TargetSelector.parse(text.toString(), null)
                .configure(ITargetSelector.Configure.SELECT_MEMBER);
            var selected = menu.methods.stream().filter(method -> selector.match(ElementNode.of(menu, method))
                .isExactMatch()).limit(selector.getMaxMatchCount()).toList();
            for (var method : selected) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && target.equals("L" + call.owner + ";" + call.name + call.desc)) matches++;
                }
            }
        }
        assertEquals(1, matches, "The required confirmation hook must reach exactly one submitJob invocation");
    }

    private static Object value(AnnotationNode annotation, String key) {
        int index = annotation.values.indexOf(key);
        return index < 0 ? null : annotation.values.get(index + 1);
    }

    private static ClassNode readResource(String name) throws Exception {
        try (var input = GtoCraftConfirmMixinCompatibilityTest.class.getResourceAsStream("/" + name + ".class")) {
            return read(input);
        }
    }

    private static ClassNode read(InputStream input) throws Exception {
        assertNotNull(input);
        var node = new ClassNode();
        new ClassReader(input).accept(node, 0);
        return node;
    }
}
