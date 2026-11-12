package cn.dancingsnow.neoecoae.client.rendering;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Client-only, time-limited see-through outlines for blocks located from a GUI (e.g. pattern providers found via
 * "locate pattern"). Highlights are filtered by dimension and pulse until they expire.
 */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class BlockHighlightRenderer {
    private static final long DURATION_MS = 15_000L;
    private static final List<Highlight> HIGHLIGHTS = new ArrayList<>();
    private static final MultiBufferSource.BufferSource BUFFERS =
        MultiBufferSource.immediate(new BufferBuilder(RenderType.TRANSIENT_BUFFER_SIZE));

    private record Highlight(ResourceLocation dimension, BlockPos position, long expiresAt) {}

    private BlockHighlightRenderer() {}

    /** Replaces the current highlights with the given positions in the given dimension. */
    public static void highlight(ResourceLocation dimension, List<BlockPos> positions) {
        long expiresAt = Util.getMillis() + DURATION_MS;
        HIGHLIGHTS.clear();
        for (var position : positions) HIGHLIGHTS.add(new Highlight(dimension, position.immutable(), expiresAt));
    }

    /** Adds one highlight without clearing the others. */
    public static void add(ResourceLocation dimension, BlockPos position) {
        HIGHLIGHTS.removeIf(h -> h.dimension.equals(dimension) && h.position.equals(position));
        HIGHLIGHTS.add(new Highlight(dimension, position.immutable(), Util.getMillis() + DURATION_MS));
    }

    public static void clear() {
        HIGHLIGHTS.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || HIGHLIGHTS.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) {
            HIGHLIGHTS.clear();
            return;
        }
        long now = Util.getMillis();
        HIGHLIGHTS.removeIf(h -> h.expiresAt <= now);
        if (HIGHLIGHTS.isEmpty()) return;

        ResourceLocation dimension = level.dimension().location();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        float pulse = 0.55F + 0.45F * (float) Math.sin(now / 150.0);
        var consumer = BUFFERS.getBuffer(HighlightRenderType.SEE_THROUGH_LINES);
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (var highlight : HIGHLIGHTS) {
            if (!highlight.dimension.equals(dimension)) continue;
            AABB box = new AABB(highlight.position).inflate(0.004);
            LevelRenderer.renderLineBox(pose, consumer, box, 1.0F, 0.85F, 0.1F, pulse);
        }
        pose.popPose();
        BUFFERS.endBatch(HighlightRenderType.SEE_THROUGH_LINES);
    }

    /** Subclass only to reach RenderType's protected render-state shards. */
    private static final class HighlightRenderType extends RenderType {
        static final RenderType SEE_THROUGH_LINES = create("neoecoae_see_through_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER)
                .setLineState(new LineStateShard(OptionalDouble.of(3.0)))
                .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST)
                .createCompositeState(false));

        private HighlightRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                    boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
        }
    }
}
