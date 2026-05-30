package cn.dancingsnow.neoecoae.multiblock;

import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a list of required materials for a given multiblock structure length
 * by recording block placements through a {@link RecordingMultiBlockContext}.
 */
public final class StructureTerminalMaterialRequirements {

    private StructureTerminalMaterialRequirements() {
    }

    /**
     * Computes the material list for a player building a structure of the
     * given length on the given host.
     *
     * @param player the player (used for inventory lookup)
     * @param host   the multiblock build host
     * @param length the desired structure length
     * @return list of material entries with required/available counts
     */
    public static List<NEStructureTerminalUiState.BuildMaterialEntry> getMaterialsForPlayer(
            ServerPlayer player, INEMultiblockBuildHost host, int length) {

        if (host.getBuildDefinition() == null) {
            return List.of();
        }

        RecordingMultiBlockContext ctx = RecordingMultiBlockContext.record(
                host, player.level(), host.getHostPos(), length);

        List<ItemStack> raw = ctx.getRequiredItems();
        List<NEStructureTerminalUiState.BuildMaterialEntry> result = new ArrayList<>();

        for (ItemStack required : raw) {
            if (required.isEmpty())
                continue;
            int available = countAvailable(player, required);
            result.add(new NEStructureTerminalUiState.BuildMaterialEntry(required, required.getCount(), available));
        }

        return result;
    }

    private static int countAvailable(ServerPlayer player, ItemStack required) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameTags(stack, required)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
