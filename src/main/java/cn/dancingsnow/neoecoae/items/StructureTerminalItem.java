package cn.dancingsnow.neoecoae.items;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalActionExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Structure Terminal — a handheld tool for configuring and executing
 * multiblock structure builds.
 *
 * <p>Behaviour:
 * <ul>
 *   <li><b>Right-click (air or block, no shift)</b>: Opens the terminal
 *       config UI to set the build length. The length is stored in the
 *       item's NBT under key {@value #TAG_BUILD_LENGTH}.</li>
 *   <li><b>Shift + right-click on a multiblock host</b>: Executes
 *       {@link INEMultiblockBuildHost#autoBuild(ServerPlayer, int)}
 *       using the length stored in this item.</li>
 *   <li><b>Shift + right-click on a non-host block</b>: Passes through.</li>
 * </ul>
 */
public class StructureTerminalItem extends Item {

    public static final String TAG_BUILD_LENGTH = "BuildLength";
    public static final String TAG_MODE = "Mode";
    public static final int DEFAULT_BUILD_LENGTH = 1;
    public static final int MIN_BUILD_LENGTH = 1;

    public StructureTerminalItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    // ── Global max build length (repeat count / variable sections) ──

    /** Maximum repeat count across all three multiblock systems. */
    public static int getGlobalMaxBuildLength() {
        int crafting = Math.max(1, NEConfig.craftingSystemMaxLength - 4);
        int computation = Math.max(1, NEConfig.computationSystemMaxLength - 4);
        int storage = Math.max(1, NEConfig.storageSystemMaxLength - 3);
        return Math.max(storage, Math.max(crafting, computation));
    }

    // ── ItemStack NBT helpers (length = repeat count / variable sections) ──

    public static int getBuildLength(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BUILD_LENGTH)) {
            return DEFAULT_BUILD_LENGTH;
        }
        return Mth.clamp(tag.getInt(TAG_BUILD_LENGTH), MIN_BUILD_LENGTH, getGlobalMaxBuildLength());
    }

    public static void setBuildLength(ItemStack stack, int length) {
        stack.getOrCreateTag().putInt(TAG_BUILD_LENGTH,
            Mth.clamp(length, MIN_BUILD_LENGTH, getGlobalMaxBuildLength()));
    }

    // ── Mode NBT helpers ──

    public static StructureTerminalMode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MODE)) {
            return StructureTerminalMode.BUILD;
        }
        try {
            return StructureTerminalMode.valueOf(tag.getString(TAG_MODE));
        } catch (IllegalArgumentException ex) {
            return StructureTerminalMode.BUILD;
        }
    }

    public static void setMode(ItemStack stack, StructureTerminalMode mode) {
        stack.getOrCreateTag().putString(TAG_MODE, mode.name());
    }

    // ── Item behaviour ──

    /**
     * Right-click in air → open terminal config UI.
     * Shift + right-click in air → no action (blocked).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Shift + air → do nothing, prevent config UI
        if (player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            openTerminalConfig(serverPlayer, hand);
            return InteractionResultHolder.consume(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    /**
     * Right-click on a block.
     * <ul>
     *   <li>No shift → open terminal config UI (same as air right-click).</li>
     *   <li>Shift + host → execute auto-build with stored length.</li>
     *   <li>Shift + non-host → PASS.</li>
     * </ul>
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();

        if (!player.isShiftKeyDown()) {
            // Normal right-click on any block → open config UI (with hostPos if host)
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                BlockEntity be = level.getBlockEntity(pos);
                BlockPos hostPos = be instanceof INEMultiblockBuildHost ? pos : null;
                openTerminalConfig(serverPlayer, ctx.getHand(), hostPos);
            }
            return InteractionResult.CONSUME;
        }

        // Shift + right-click → mode-based execution
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof INEMultiblockBuildHost host)) {
            return InteractionResult.CONSUME;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        int requestedLength = StructureTerminalItem.getBuildLength(stack);
        StructureTerminalMode mode = StructureTerminalItem.getMode(stack);
        ServerPlayer serverPlayer = (ServerPlayer) player;

        switch (mode) {
            case BUILD -> StructureTerminalActionExecutor.build(serverPlayer, host, requestedLength);
            case DISMANTLE -> StructureTerminalActionExecutor.dismantle(serverPlayer, host);
            case EXPAND -> StructureTerminalActionExecutor.expand(serverPlayer, host, requestedLength);
        }

        return InteractionResult.CONSUME;
    }

    // ── Internal ──

    private void openTerminalConfig(ServerPlayer player, InteractionHand hand) {
        openTerminalConfig(player, hand, null);
    }

    private void openTerminalConfig(ServerPlayer player, InteractionHand hand, @Nullable BlockPos hostPos) {
        NetworkHooks.openScreen(
            player,
            new SimpleMenuProvider(
                (windowId, inv, p) -> new NEStructureTerminalMenu(windowId, inv, hand, hostPos),
                Component.translatable("item.neoecoae.structure_terminal")
            ),
            buf -> {
                buf.writeEnum(hand);
                buf.writeBoolean(hostPos != null);
                if (hostPos != null) {
                    buf.writeBlockPos(hostPos);
                }
            }
        );
    }
}
