package cn.dancingsnow.neoecoae.items;

import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.core.localization.Tooltips;
import appeng.items.contents.CellConfig;
import appeng.items.storage.StorageCellTooltipComponent;
import appeng.util.ConfigInventory;
import appeng.util.InteractionUtil;
import cn.dancingsnow.neoecoae.all.NECellTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.compat.ae2.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellMetadata;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellMode;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import com.tterrag.registrate.util.entry.RegistryEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ECOStorageCellItem extends Item implements IBasicECOCellItem {
    private static final String NBT_INFINITE_STORAGE_MATRIX_LOCKED = "neo_infiniteStorageMatrixLocked";

    @Getter
    private final IECOTier tier;

    private final long fallbackTotalBytes;
    private final int bytesPerType;
    private final int totalTypes;
    private final AEKeyType keyType;
    private final RegistryEntry<ECOCellType> cellType;

    public ECOStorageCellItem(
            Properties properties, IECOTier tier, AEKeyType keyType, RegistryEntry<ECOCellType> cellType) {
        super(properties);
        this.tier = tier;
        this.fallbackTotalBytes = tier.getStorageTotalBytes();
        this.bytesPerType = 1 << (12 + tier.getTier());
        this.totalTypes = tier.getStorageTotalTypes(keyType);
        this.keyType = keyType;
        this.cellType = cellType;
    }

    @Override
    public AEKeyType getKeyType() {
        return keyType;
    }

    @Override
    public long getBytes() {
        return NEConfig.getEcoStorageCellCapacity(tier, fallbackTotalBytes);
    }

    @Override
    public long getIdleDrainBytes() {
        return fallbackTotalBytes;
    }

    @Override
    public int getBytesPerType() {
        return bytesPerType;
    }

    @Override
    public int getTotalTypes() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ECOCellType getCellType() {
        return cellType.get();
    }

    @Override
    public void appendHoverText(
            ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag tooltipFlag) {
        ECOStorageCellMode mode = ECOStorageCellMetadata.getMode(stack);
        if (mode == ECOStorageCellMode.DOMAIN_MEMBER) {
            lines.add(Component.literal("Mode: Infinite storage member").withStyle(ChatFormatting.GOLD));
            lines.add(Component.literal("State: Managed by ECO storage host").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("Empty the host domain to unbind").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (mode == ECOStorageCellMode.MIGRATING) {
            lines.add(Component.literal("Mode: Migrating to infinite domain").withStyle(ChatFormatting.GOLD));
            lines.add(Component.literal("Storage access is temporarily locked").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (ECOStorageCellMetadata.isLegacyInfiniteLocked(stack)) {
            lines.add(Component.literal("Legacy infinite matrix lock").withStyle(ChatFormatting.GOLD));
        }

        var handler = getCellInventory(stack);
        if (handler == null) {
            return;
        }
        lines.add(Tooltips.bytesUsed(handler.getUsedBytes(), handler.getTotalBytes()));
        if (handler.getTotalItemTypes() == Long.MAX_VALUE) {
            lines.add(Component.literal("Types: " + NELDLibText.typeCount(handler.getStoredItemTypes()) + " / "
                            + NELDLibText.INFINITE)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Tooltips.typesUsed(handler.getStoredItemTypes(), handler.getTotalItemTypes()));
        }
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        if (ECOStorageCellMetadata.hasNonPortableState(stack)) {
            return Optional.empty();
        }

        var handler = getCellInventory(stack);
        if (handler == null) {
            return Optional.empty();
        }

        var upgradeStacks = new ArrayList<ItemStack>();
        if (AEConfig.instance().isTooltipShowCellUpgrades()) {
            for (var upgrade : handler.getUpgradesInventory()) {
                upgradeStacks.add(upgrade);
            }
        }

        boolean hasMoreContent;
        List<GenericStack> content;
        if (AEConfig.instance().isTooltipShowCellContent()) {
            content = new ArrayList<>();

            var maxCountShown = AEConfig.instance().getTooltipMaxCellContentShown();
            content.addAll(handler.getStoredStacks());

            if (content.size() < maxCountShown && handler.getPartitionListMode() == IncludeExclude.WHITELIST) {
                var config = handler.getConfigInventory();
                for (int i = 0; i < config.size(); i++) {
                    var what = config.getKey(i);
                    if (what != null && !containsContentKey(content, what)) {
                        content.add(new GenericStack(what, 0));
                    }
                    if (content.size() > maxCountShown) {
                        break;
                    }
                }
            }

            content.sort(Comparator.comparingLong(GenericStack::amount).reversed());
            hasMoreContent = content.size() > maxCountShown;
            if (content.size() > maxCountShown) {
                content.subList(maxCountShown, content.size()).clear();
            }
        } else {
            hasMoreContent = false;
            content = Collections.emptyList();
        }

        return Optional.of(new StorageCellTooltipComponent(upgradeStacks, content, hasMoreContent, true));
    }

    @Nullable public static ECOStorageCell getCellInventory(ItemStack stack) {
        return getCellInventory(stack, null);
    }

    @Nullable public static ECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        if (stack.getItem() instanceof ECOStorageCellItem) {
            if (ECOStorageCellMetadata.hasNonPortableState(stack)) {
                return null;
            }
            return new ECOStorageCell(stack, host);
        }
        return null;
    }

    public static boolean isInfiniteStorageMatrixLocked(ItemStack stack) {
        return ECOStorageCellMetadata.isDomainMember(stack)
                || ECOStorageCellMetadata.isMigrating(stack)
                || ECOStorageCellMetadata.isLegacyInfiniteLocked(stack);
    }

    public static boolean lockInfiniteStorageMatrix(ItemStack stack) {
        if (!(stack.getItem() instanceof ECOStorageCellItem) || isInfiniteStorageMatrixLocked(stack)) {
            return false;
        }
        stack.getOrCreateTag().putBoolean(NBT_INFINITE_STORAGE_MATRIX_LOCKED, true);
        return true;
    }

    private static boolean containsContentKey(List<GenericStack> content, AEKey key) {
        for (GenericStack stack : content) {
            if (stack.what().equals(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        if (is.hasTag() && is.getTag().contains("fuzzyMode")) {
            try {
                return FuzzyMode.valueOf(is.getTag().getString("fuzzyMode"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        is.getOrCreateTag().putString("fuzzyMode", fzMode.name());
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(key -> key.getType() == keyType, is);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, 4);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        this.disassembleDrive(player.getItemInHand(hand), level, player);
        return new InteractionResultHolder<>(
                InteractionResult.sidedSuccess(level.isClientSide()), player.getItemInHand(hand));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return this.disassembleDrive(stack, context.getLevel(), context.getPlayer())
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide())
                : InteractionResult.PASS;
    }

    private boolean disassembleDrive(ItemStack stack, Level level, Player player) {
        if (!InteractionUtil.isInAlternateUseMode(player)) {
            return false;
        }
        if (ECOStorageCellMetadata.hasNonPortableState(stack) || isInfiniteStorageMatrixLocked(stack)) {
            player.displayClientMessage(Component.literal("Infinite storage members cannot be disassembled"), true);
            return false;
        }

        List<ItemStack> disassembledStacks = StorageCellDisassemblyRecipe.getDisassemblyResult(level, stack.getItem());
        if (disassembledStacks.isEmpty()) {
            return false;
        }

        Inventory playerInventory = player.getInventory();
        if (playerInventory.getSelected() != stack) {
            return false;
        }

        ECOStorageCell cellInventory = getCellInventory(stack);
        if (cellInventory != null && cellInventory.getStoredItemTypes() > 0) {
            player.displayClientMessage(PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true);
            return false;
        }

        playerInventory.setItem(playerInventory.selected, ItemStack.EMPTY);

        for (var disassembledStack : disassembledStacks) {
            playerInventory.placeItemBackInInventory(disassembledStack.copy());
        }

        getUpgrades(stack).forEach(playerInventory::placeItemBackInInventory);

        return true;
    }

    public static class ItemCellHandler implements IECOCellHandler {
        public static final ItemCellHandler INSTANCE = new ItemCellHandler();

        @Override
        public boolean isCell(ItemStack stack) {
            if (stack.getItem() instanceof ECOStorageCellItem item) {
                return item.getCellType() == NECellTypes.ITEM.get() && item.getKeyType() == AEKeyType.items();
            }
            return false;
        }

        @Override
        public @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
            return ECOStorageCellItem.getCellInventory(is, host);
        }
    }

    public static class FluidCellHandler implements IECOCellHandler {
        public static final FluidCellHandler INSTANCE = new FluidCellHandler();

        @Override
        public boolean isCell(ItemStack stack) {
            if (stack.getItem() instanceof ECOStorageCellItem item) {
                return item.getCellType() == NECellTypes.FLUID.get() && item.getKeyType() == AEKeyType.fluids();
            }
            return false;
        }

        @Override
        public @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
            return ECOStorageCellItem.getCellInventory(is, host);
        }
    }

    /**
     * @deprecated Replaced by {@link ItemCellHandler} and {@link FluidCellHandler}.
     */
    @Deprecated
    public static class Handler implements IECOCellHandler {
        public static final Handler INSTANCE = new Handler();

        @Override
        public boolean isCell(ItemStack stack) {
            return stack.getItem() instanceof ECOStorageCellItem;
        }

        @Override
        public @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
            return ECOStorageCellItem.getCellInventory(is, host);
        }
    }
}
