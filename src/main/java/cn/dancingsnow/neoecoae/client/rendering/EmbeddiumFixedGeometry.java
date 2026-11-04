package cn.dancingsnow.neoecoae.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.embeddedt.embeddium.api.ChunkMeshEvent;
import org.embeddedt.embeddium.api.MeshAppender;

import java.util.ArrayList;
import java.util.List;

/** Embeddium replaces the native rebuild task which normally emits our fixed model geometry. */
public final class EmbeddiumFixedGeometry {
    private EmbeddiumFixedGeometry() {}

    public static void register() {
        ChunkMeshEvent.BUS.addListener(EmbeddiumFixedGeometry::onMesh);
    }

    static void onMesh(ChunkMeshEvent event) {
        var section = event.getSectionOrigin();
        var chunk = event.getWorld().getChunk(section.x(), section.z(), ChunkStatus.FULL, false);
        if (!(chunk instanceof LevelChunk levelChunk)) return;
        List<BlockPos> positions = new ArrayList<>();
        for (var entity : levelChunk.getBlockEntities().values()) {
            var pos = entity.getBlockPos();
            if (pos.getY() >= section.minBlockY() && pos.getY() <= section.maxBlockY()
                && FixedBlockEntityRenderers.hasRenderer(entity)) {
                positions.add(pos.immutable());
            }
        }
        if (!positions.isEmpty()) {
            event.addMeshAppender(context -> render(context, positions));
        }
    }

    private static void render(MeshAppender.Context context, List<BlockPos> positions) {
        // The appender runs on a chunk worker. Resolve entities and lighting through its render view,
        // never through the live client world captured by the main-thread event.
        var region = context.blockRenderView();
        List<BlockEntity> entities = new ArrayList<>(positions.size());
        for (var pos : positions) {
            var entity = region.getBlockEntity(pos);
            if (FixedBlockEntityRenderers.hasRenderer(entity)) entities.add(entity);
        }
        FixedBlockEntityRenderers.renderSection(region, context.sectionOrigin().origin(), new PoseStack(),
            context.vertexConsumerProvider()::apply, entities);
    }
}
