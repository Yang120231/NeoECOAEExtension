package cn.dancingsnow.neoecoae.client.multiblock.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NEMultiblockSceneRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(NEMultiblockSceneRenderer.class);
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    private static final Set<String> LOGGED_RENDER_FAILURES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final float DEFAULT_YAW = -38.0F;
    private static final float DEFAULT_PITCH = 28.0F;
    private static final float DEFAULT_ZOOM = 0.90F;
    private static final float MIN_ZOOM = 0.15F;
    private static final float MAX_ZOOM = 1.80F;
    private static final float ZOOM_STEP = 0.10F;
    private static final float FIT_PADDING = 0.68F;
    private static final float MOUSE_YAW_SPEED = 0.6F;
    private static final float MOUSE_PITCH_SPEED = 0.45F;
    private static final float PITCH_MIN = -75.0F;
    private static final float PITCH_MAX = 75.0F;

    private float yaw = DEFAULT_YAW;
    private float pitch = DEFAULT_PITCH;
    private float zoom = DEFAULT_ZOOM;

    public void render(
            GuiGraphics g, MultiblockPreviewScene scene, int x, int y, int width, int height, float partialTick) {
        render(g, scene, scene == null ? List.of() : scene.orderedPositions(), x, y, width, height, partialTick);
    }

    public void render(
            GuiGraphics g,
            MultiblockPreviewScene scene,
            List<BlockPos> positions,
            int x,
            int y,
            int width,
            int height,
            float partialTick) {
        render(g, scene, positions, x, y, width, height, partialTick, true);
    }

    public void render(
            GuiGraphics g,
            MultiblockPreviewScene scene,
            List<BlockPos> positions,
            int x,
            int y,
            int width,
            int height,
            float partialTick,
            boolean clip) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || scene == null || scene.isEmpty()) {
            drawEmptyScene(g, x, y, width, height);
            return;
        }

        SceneBounds cameraBounds = SceneBounds.full(scene);
        if (cameraBounds.maxDimension() <= 0 || width <= 0 || height <= 0) {
            drawEmptyScene(g, x, y, width, height);
            return;
        }

        SceneViewport viewport = new SceneViewport(x, y, width, height);
        MultiBufferSource.BufferSource buffer = null;
        g.flush();
        if (clip) {
            PreviewScissor.enable(g, viewport);
        }
        beginCompatSceneState();

        PoseStack pose = g.pose();
        pose.pushPose();
        try {
            clearSceneDepth();
            CameraFit.ProjectedBounds projectedBounds = CameraFit.project(cameraBounds, yaw, pitch);
            float scale = CameraFit.calculateStableScale(cameraBounds, width, height, FIT_PADDING) * zoom;
            pose.translate(x + width * 0.5F, y + height * 0.50F, 240.0F);
            pose.translate(-projectedBounds.centerX() * scale, projectedBounds.centerY() * scale, 0.0F);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(-cameraBounds.centerX(), -cameraBounds.centerY(), -cameraBounds.centerZ());

            BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
            buffer = minecraft.renderBuffers().bufferSource();
            Map<BlockPos, BlockState> blocks = scene.blocks();
            renderOpaquePass(dispatcher, buffer, pose, blocks, positions);
            buffer.endBatch();
            renderTranslucentPass(dispatcher, buffer, pose, blocks, positions, yaw, pitch);
        } finally {
            if (buffer != null) {
                buffer.endBatch();
            }
            pose.popPose();
            if (clip) {
                g.disableScissor();
            }
            endCompatSceneState(g);
        }
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setZoom(float zoom) {
        this.zoom = Mth.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    }

    public void adjustZoom(double scrollDelta) {
        if (scrollDelta == 0.0D) {
            return;
        }
        setZoom(this.zoom + (float) Math.signum(scrollDelta) * ZOOM_STEP);
    }

    public void resetView() {
        this.yaw = DEFAULT_YAW;
        this.pitch = DEFAULT_PITCH;
        this.zoom = DEFAULT_ZOOM;
    }

    public void rotate(float yawDelta, float pitchDelta) {
        this.yaw += yawDelta;
        this.pitch = Math.max(PITCH_MIN, Math.min(PITCH_MAX, this.pitch + pitchDelta));
    }

    /**
     * Unified mouse-drag rotation for JEI and EMI multiblock previews.
     * Applies sensitivity internally so callers don't need to multiply.
     */
    public void rotateFromMouseDrag(double dragX, double dragY) {
        rotate((float) dragX * MOUSE_YAW_SPEED, (float) dragY * MOUSE_PITCH_SPEED);
    }

    private static void beginCompatSceneState() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
    }

    private static void clearSceneDepth() {
        RenderSystem.depthMask(true);
        RenderSystem.clearDepth(1.0D);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private static void endCompatSceneState(GuiGraphics g) {
        g.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        Lighting.setupFor3DItems();
    }

    private static void renderOpaquePass(
            BlockRenderDispatcher dispatcher,
            MultiBufferSource buffer,
            PoseStack pose,
            Map<BlockPos, BlockState> blocks,
            List<BlockPos> positions) {
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        for (BlockPos pos : positions) {
            BlockState state = blocks.get(pos);
            if (!isRenderable(state) || isTranslucent(state)) {
                continue;
            }
            renderBlock(dispatcher, buffer, pose, pos, state);
        }
    }

    private static void renderTranslucentPass(
            BlockRenderDispatcher dispatcher,
            MultiBufferSource.BufferSource buffer,
            PoseStack pose,
            Map<BlockPos, BlockState> blocks,
            List<BlockPos> positions,
            float yaw,
            float pitch) {
        List<BlockPos> translucentPositions = collectTranslucentPositions(blocks, positions, yaw, pitch);
        if (translucentPositions.isEmpty()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        for (BlockPos pos : translucentPositions) {
            BlockState state = blocks.get(pos);
            renderBlock(dispatcher, buffer, pose, pos, state);
        }
        buffer.endBatch(RenderType.translucent());
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static List<BlockPos> collectTranslucentPositions(
            Map<BlockPos, BlockState> blocks, List<BlockPos> positions, float yaw, float pitch) {
        List<BlockPos> translucent = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockState state = blocks.get(pos);
            if (isRenderable(state) && isTranslucent(state)) {
                translucent.add(pos);
            }
        }
        translucent.sort(Comparator.comparingDouble(pos -> viewDepth(pos, yaw, pitch)));
        return translucent;
    }

    private static boolean isRenderable(BlockState state) {
        return state != null && !state.isAir() && state.getRenderShape() != RenderShape.INVISIBLE;
    }

    private static boolean isTranslucent(BlockState state) {
        return ItemBlockRenderTypes.getChunkRenderType(state) == RenderType.translucent();
    }

    private static double viewDepth(BlockPos pos, float yaw, float pitch) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double yawZ = -x * sinYaw + z * cosYaw;
        return y * Math.sin(pitchRad) + yawZ * Math.cos(pitchRad);
    }

    private static void renderBlock(
            BlockRenderDispatcher dispatcher,
            MultiBufferSource buffer,
            PoseStack pose,
            BlockPos pos,
            BlockState state) {
        pose.pushPose();
        try {
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            dispatcher.renderSingleBlock(state, pose, buffer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException e) {
            String key = state.toString();
            if (LOGGED_RENDER_FAILURES.add(key)) {
                LOGGER.debug("Skipping block in EMI multiblock preview after render failure: {}", key, e);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void drawEmptyScene(GuiGraphics g, int x, int y, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Component text = Component.translatable("emi.neoecoae.multiblock.empty_scene");
        int textX = x + Math.max(0, (width - font.width(text)) / 2);
        int textY = y + Math.max(0, (height - font.lineHeight) / 2);
        g.drawString(font, text, textX, textY, 0xFF777777, false);
    }
}
