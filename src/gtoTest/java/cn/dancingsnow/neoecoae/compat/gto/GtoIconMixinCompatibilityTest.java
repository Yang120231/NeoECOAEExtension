package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import cn.dancingsnow.neoecoae.client.gui.workstation.WorkstationInterfaceArtwork;
import cn.dancingsnow.neoecoae.mixins.client.ae2.WorkstationInterfaceIconMixin;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class GtoIconMixinCompatibilityTest {
    @Test
    void copiedIconMethodDoesNotNeedInterfaceSuperRebinding() throws Exception {
        var node = new ClassNode();
        try (var input = WorkstationInterfaceIconMixin.class.getResourceAsStream("WorkstationInterfaceIconMixin.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        var blitter = node.methods.stream().filter(method -> method.name.equals("getBlitter"))
            .findFirst().orElseThrow();
        for (var instruction : blitter.instructions) {
            assertFalse(instruction instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESPECIAL && call.itf,
                "Interface-super calls are rebound to Icon and fail with IncompatibleClassChangeError in production");
        }
    }

    @Test
    void ordinaryScreensKeepTheNativeIconRectangle() {
        try (var artwork = mockStatic(WorkstationInterfaceArtwork.class)) {
            artwork.when(WorkstationInterfaceArtwork::active).thenReturn(false);
            var icon = new WorkstationInterfaceIconMixin() {
                @Override public ResourceLocation getIconTexture() { return new ResourceLocation("ae2", "textures/guis/states.png"); }
                @Override public int getIconTextureWidth() { return 512; }
                @Override public int getIconTextureHeight() { return 512; }
                @Override public int getIconX() { return 23; }
                @Override public int getIconY() { return 41; }
                @Override public int getIconWidth() { return 9; }
                @Override public int getIconHeight() { return 14; }
            };
            var blitter = icon.getBlitter();
            assertEquals(23, blitter.getSrcX());
            assertEquals(41, blitter.getSrcY());
            assertEquals(9, blitter.getSrcWidth());
            assertEquals(14, blitter.getSrcHeight());
        }
    }
}
