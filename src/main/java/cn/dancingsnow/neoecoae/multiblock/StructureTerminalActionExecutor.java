package cn.dancingsnow.neoecoae.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Executes Structure Terminal actions: BUILD, DISMANTLE, EXPAND.
 * <p>
 * All world-modifying operations run on the server side only.
 * </p>
 */
public final class StructureTerminalActionExecutor {

    private static final BlockPos RELATIVE_HOST_POS = new BlockPos(1, 1, 0);

    private StructureTerminalActionExecutor() {
    }

    /**
     * Builds a multiblock structure at the given length.
     */
    public static void build(ServerPlayer player, INEMultiblockBuildHost host, int length) {
        host.autoBuild(player, length);
    }

    /**
     * Dismantles the existing multiblock structure, returning blocks to the player.
     */
    public static void dismantle(ServerPlayer player, INEMultiblockBuildHost host) {
        if (!host.isFormed()) {
            player.displayClientMessage(Component.literal("Structure is not formed, nothing to dismantle."), true);
            return;
        }
        if (host.getBuildDefinition() == null) {
            return;
        }

        int currentLength = host.getSelectedBuildLength();
        RecordingMultiBlockContext ctx = RecordingMultiBlockContext.record(
                host, player.level(), host.getHostPos(), currentLength);

        BlockPos hostPos = host.getHostPos();
        for (BlockPos relativePos : ctx.allBlocks()) {
            BlockPos worldPos = hostPos.offset(
                    relativePos.getX() - RELATIVE_HOST_POS.getX(),
                    relativePos.getY() - RELATIVE_HOST_POS.getY(),
                    relativePos.getZ() - RELATIVE_HOST_POS.getZ());
            if (worldPos.equals(hostPos))
                continue;

            BlockState state = player.level().getBlockState(worldPos);
            if (state.isAir())
                continue;

            ItemStack drop = state.getBlock().asItem().getDefaultInstance();
            player.level().setBlockAndUpdate(worldPos, Blocks.AIR.defaultBlockState());
            if (!drop.isEmpty()) {
                if (!player.getInventory().add(drop)) {
                    Containers.dropItemStack(player.level(), worldPos.getX(), worldPos.getY(), worldPos.getZ(), drop);
                }
            }
        }

        // Clear formed state if possible
        if (host instanceof cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<?, ?> neBe) {
            neBe.setFormed(false);
        }
        host.setSelectedBuildLength(currentLength); // keep length, just clear formed
    }

    /**
     * Expands a structure from oldLength to requestedLength.
     */
    public static void expand(ServerPlayer player, INEMultiblockBuildHost host, int requestedLength) {
        int oldLength = host.getSelectedBuildLength();
        if (requestedLength <= oldLength) {
            player.displayClientMessage(Component.literal("New length not greater than current, no expansion needed."),
                    true);
            return;
        }
        // For now, delegate to build with the new length (autoBuild handles delta)
        host.autoBuild(player, requestedLength);
    }
}
