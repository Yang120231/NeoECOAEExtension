package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import cn.dancingsnow.neoecoae.items.StructureTerminalMode;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMaterialRequirements;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import java.util.List;

/**
 * Menu for the Structure Terminal configuration UI.
 * <p>
 * This menu is bound to a {@link StructureTerminalItem} in the player's
 * hand (identified by {@link InteractionHand}), not to a world BlockEntity.
 * It allows the player to set the build length stored in the item's NBT.
 * </p>
 */
public class NEStructureTerminalMenu extends AbstractContainerMenu {

    private final InteractionHand hand;
    @Nullable
    private final BlockPos hostPos;
    private int buildLength;

    public NEStructureTerminalMenu(int containerId, Inventory playerInv, InteractionHand hand) {
        this(containerId, playerInv, hand, null);
    }

    public NEStructureTerminalMenu(int containerId, Inventory playerInv, InteractionHand hand,
            @Nullable BlockPos hostPos) {
        super(NENativeMenus.STRUCTURE_TERMINAL.get(), containerId);
        this.hand = hand;
        this.hostPos = hostPos;
        this.buildLength = StructureTerminalItem.getBuildLength(playerInv.player.getItemInHand(hand));
    }

    public InteractionHand getHand() {
        return hand;
    }

    public int getBuildLength() {
        return buildLength;
    }

    public void setBuildLength(int length) {
        this.buildLength = length;
    }

    /**
     * Returns the Structure Terminal ItemStack this menu is bound to, or null.
     */
    @Nullable
    public BlockPos getHostPos() {
        return hostPos;
    }

    @Nullable
    public INEMultiblockBuildHost getHost(Player player) {
        if (hostPos == null)
            return null;
        BlockEntity be = player.level().getBlockEntity(hostPos);
        return be instanceof INEMultiblockBuildHost host ? host : null;
    }

    @Nullable
    public ItemStack getTerminalStack(Player player) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof StructureTerminalItem) {
            return stack;
        }
        return null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof StructureTerminalItem;
    }

    /**
     * Sends the current build length + mode + materials state to the client.
     */
    public void syncToClient(ServerPlayer player) {
        ItemStack stack = getTerminalStack(player);
        int length = stack != null ? StructureTerminalItem.getBuildLength(stack)
                : StructureTerminalItem.DEFAULT_BUILD_LENGTH;
        int min = StructureTerminalItem.MIN_BUILD_LENGTH;
        int max = StructureTerminalItem.getGlobalMaxBuildLength();
        StructureTerminalMode mode = stack != null ? StructureTerminalItem.getMode(stack) : StructureTerminalMode.BUILD;
        this.buildLength = length;

        List<NEStructureTerminalUiState.BuildMaterialEntry> materials = List.of();
        if (mode == StructureTerminalMode.BUILD) {
            INEMultiblockBuildHost host = getHost(player);
            if (host != null) {
                materials = StructureTerminalMaterialRequirements.getMaterialsForPlayer(player, host, length);
            }
        }

        NENetwork.NEStructureTerminalConfigPacket pkt = new NENetwork.NEStructureTerminalConfigPacket(length, min, max,
                mode, materials);
        NENetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }
}
