package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.items.storage.CreativeCellItem;
import appeng.me.cells.BasicCellInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public enum ExternalAE2CellHandler implements IECOCellHandler {
    INSTANCE;

    private static final ResourceLocation SOURCE_TYPE_ID = NeoECOAE.id("source");
    private static final ResourceLocation MANA_TYPE_ID = NeoECOAE.id("mana");
    private static final ECOCellType SOURCE_TYPE =
            new ECOCellType(SOURCE_TYPE_ID, Component.translatable("cell_type.neoecoae.source"));
    private static final ECOCellType MANA_TYPE =
            new ECOCellType(MANA_TYPE_ID, Component.translatable("cell_type.neoecoae.mana"));

    @Override
    public boolean isCell(ItemStack stack) {
        return externalType(stack) != null && StorageCells.getHandler(stack) != null;
    }

    @Override
    public @Nullable IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        ExternalType type = externalType(stack);
        if (type == null) {
            return null;
        }
        ICellHandler handler = StorageCells.getHandler(stack);
        StorageCell cell = handler == null ? null : handler.getCellInventory(stack, host);
        if (cell == null) {
            return null;
        }
        return new Adapter(cell, stack.copyWithCount(1), type);
    }

    @Nullable private static ExternalType externalType(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return null;
        }
        String namespace = id.getNamespace();
        String path = id.getPath();
        if ("arseng".equals(namespace)
                && ("creative_source_cell".equals(path)
                        || path.startsWith("source_storage_cell_")
                        || path.startsWith("portable_source_cell_"))) {
            return new ExternalType(SOURCE_TYPE, tierFromCellPath(path));
        }
        if ("appbot".equals(namespace)
                && ("creative_mana_cell".equals(path)
                        || path.startsWith("mana_storage_cell_")
                        || path.startsWith("portable_mana_storage_cell_"))) {
            return new ExternalType(MANA_TYPE, tierFromCellPath(path));
        }
        return null;
    }

    private static IECOTier tierFromCellPath(String path) {
        if (path.contains("creative") || path.contains("256k")) {
            return ECOTier.L9;
        }
        if (path.contains("64k") || path.contains("16k")) {
            return ECOTier.L6;
        }
        return ECOTier.L4;
    }

    private record ExternalType(ECOCellType cellType, IECOTier tier) {}

    private static final class Adapter implements IECOStorageCell {
        private final StorageCell delegate;
        private final ItemStack stack;
        private final ExternalType type;

        private Adapter(StorageCell delegate, ItemStack stack, ExternalType type) {
            this.delegate = delegate;
            this.stack = stack;
            this.type = type;
        }

        @Override
        public IECOTier getTier() {
            return type.tier();
        }

        @Override
        public ECOCellType getCellType() {
            return type.cellType();
        }

        @Override
        public long getStoredItemTypes() {
            if (delegate instanceof BasicCellInventory basicCell) {
                return basicCell.getStoredItemTypes();
            }
            return availableStacks().size();
        }

        @Override
        public long getTotalItemTypes() {
            if (delegate instanceof BasicCellInventory basicCell) {
                return Math.max(0L, basicCell.getTotalItemTypes());
            }
            if (stack.getItem() instanceof IBasicCellItem basicCellItem) {
                return Math.max(0, basicCellItem.getTotalTypes(stack));
            }
            if (isCreativeCell()) {
                return Long.MAX_VALUE;
            }
            return getStoredItemTypes();
        }

        @Override
        public long getUsedBytes() {
            if (delegate instanceof BasicCellInventory basicCell) {
                return Math.max(0L, basicCell.getUsedBytes());
            }
            long bytes = 0L;
            for (Reference2LongMap.Entry<AEKey> entry : availableStacks()) {
                long amount = Math.max(0L, entry.getLongValue());
                int amountPerByte = Math.max(1, entry.getKey().getAmountPerByte());
                long amountBytes = amount / amountPerByte + (amount % amountPerByte == 0 ? 0 : 1);
                bytes = saturatedAdd(bytes, amountBytes);
            }
            if (stack.getItem() instanceof IBasicCellItem basicCellItem) {
                bytes = saturatedAdd(
                        bytes, saturatedMultiply(getStoredItemTypes(), basicCellItem.getBytesPerType(stack)));
            }
            return bytes;
        }

        @Override
        public long getTotalBytes() {
            if (delegate instanceof BasicCellInventory basicCell) {
                return Math.max(0L, basicCell.getTotalBytes());
            }
            if (isCreativeCell()) {
                return Long.MAX_VALUE;
            }
            if (stack.getItem() instanceof IBasicCellItem basicCellItem) {
                return Math.max(0L, basicCellItem.getBytes(stack));
            }
            return getUsedBytes();
        }

        @Override
        public CellState getStatus() {
            return delegate.getStatus();
        }

        @Override
        public double getIdleDrain() {
            return delegate.getIdleDrain();
        }

        @Override
        public boolean canFitInsideCell() {
            return delegate.canFitInsideCell();
        }

        @Override
        public void persist() {
            delegate.persist();
        }

        @Override
        public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
            return delegate.isPreferredStorageFor(what, source);
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return delegate.insert(what, amount, mode, source);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return delegate.extract(what, amount, mode, source);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            delegate.getAvailableStacks(out);
        }

        @Override
        public Component getDescription() {
            return delegate.getDescription();
        }

        private List<Reference2LongMap.Entry<AEKey>> availableStacks() {
            KeyCounter counter = new KeyCounter();
            delegate.getAvailableStacks(counter);
            List<Reference2LongMap.Entry<AEKey>> entries = new ArrayList<>();
            for (Reference2LongMap.Entry<AEKey> entry : counter) {
                entries.add(entry);
            }
            return entries;
        }

        private boolean isCreativeCell() {
            return stack.getItem() instanceof CreativeCellItem;
        }

        private static long saturatedAdd(long left, long right) {
            if (left == Long.MAX_VALUE || right == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            if (right > 0L && left > Long.MAX_VALUE - right) {
                return Long.MAX_VALUE;
            }
            return left + right;
        }

        private static long saturatedMultiply(long left, long right) {
            if (left <= 0L || right <= 0L) {
                return 0L;
            }
            return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
        }
    }
}
