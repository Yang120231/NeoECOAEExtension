package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class ECOCraftingPatternBusBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingPatternBusBlockEntity>
    implements InternalInventoryHost, ICraftingProvider, PatternContainer, IECOPatternStorage {


    public static final int ROW_SIZE = 9;
    public static final int COL_SIZE = 7;

    private final AppEngInternalInventory inventory;
    private final List<IPatternDetails> patternDetails = new ArrayList<>();
    public final IItemHandlerModifiable itemHandler;
    private final LazyOptional<IItemHandlerModifiable> itemHandlerCap;

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patternDetails;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!(patternDetails instanceof IMolecularAssemblerSupportedPattern supportedPattern)) {
            return false;
        }
        if (cluster != null) {
            for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
                if (worker.pushPattern(supportedPattern, inputHolder)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            if (getRunningThread() >= controller.getThreadCount()) {
                return true;
            }
            for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
                if (!worker.isBusy()) {
                    return false;
                }
            }
        }
        return true;
    }

    private long getRunningThread() {
        if (cluster != null) {
            return cluster.getWorkers().stream().mapToLong(ECOCraftingWorkerBlockEntity::getRunningThreads).sum();
        }
        return 0;
    }

    @Override
    public @Nullable IGrid getGrid() {
        return getGridNode().getGrid();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return inventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        if (cluster != null && cluster.getController() != null) {
            var block = cluster.getController().getBlockState().getBlock();
            if (block != Blocks.AIR) {
                return new PatternContainerGroup(
                    AEItemKey.of(block.asItem()),
                    block.getName(),
                    List.of()
                );
            }
        }
        return new PatternContainerGroup(
            AEItemKey.of(NEBlocks.CRAFTING_PATTERN_BUS.asStack()),
            NEBlocks.CRAFTING_PATTERN_BUS.get().getName(),
            List.of()
        );
    }

    @Override
    public boolean insertPattern(ItemStack itemStack) {
        ItemStack result = inventory.addItems(itemStack.copy());
        return result.isEmpty();
    }

    class AEEncodedPatternFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return PatternDetailsHelper.decodePattern(stack, level) instanceof IMolecularAssemblerSupportedPattern;
        }
    }

    public ECOCraftingPatternBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.inventory = new AppEngInternalInventory(this, ROW_SIZE * COL_SIZE);
        this.inventory.setFilter(new AEEncodedPatternFilter());
        this.itemHandler = new AppEngInternalInventoryItemHandler(this.inventory);
        this.itemHandlerCap = LazyOptional.of(() -> this.itemHandler);
        this.getMainNode().addService(ICraftingProvider.class, this)
            .addService(IECOPatternStorage.class, this);
    }

    public void saveChangedInventory(AppEngInternalInventory inv) {
        this.saveChanges();
    }

    @Override
    public void onChangeInventory(InternalInventory inv, int slot) {
        this.saveChanges();
        updatePatternDetails();
    }

    @Override
    public void onReady() {
        super.onReady();
        updatePatternDetails();
    }

    private void updatePatternDetails() {
        patternDetails.clear();
        for (ItemStack itemStack : this.inventory) {
            IPatternDetails details = PatternDetailsHelper.decodePattern(itemStack, this.level);
            if (details != null) {
                patternDetails.add(details);
            }
        }
        ICraftingProvider.requestUpdate(this.getMainNode());
    }

    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getServer().executeIfPossible(() -> {
                setChanged();
                markForUpdate();
            });
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        IntStream.range(0, ROW_SIZE * COL_SIZE)
            .mapToObj(inventory::getStackInSlot)
            .filter(s -> !s.isEmpty())
            .forEach(drops::add);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        inventory.writeToNBT(data, "patternInventory");
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        inventory.readFromNBT(data, "patternInventory");
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerCap.invalidate();
    }

    private static final class AppEngInternalInventoryItemHandler implements IItemHandlerModifiable {
        private final AppEngInternalInventory inventory;

        private AppEngInternalInventoryItemHandler(AppEngInternalInventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public int getSlots() {
            return inventory.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            validateSlotIndex(slot);
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            validateSlotIndex(slot);

            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            if (!inventory.isItemValid(slot, stack)) {
                return stack;
            }

            ItemStack existing = inventory.getStackInSlot(slot);
            int limit = Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize());

            if (!existing.isEmpty()) {
                if (!ItemStack.isSameItemSameTags(existing, stack)) {
                    return stack;
                }
                limit -= existing.getCount();
            }

            if (limit <= 0) {
                return stack;
            }

            int inserted = Math.min(limit, stack.getCount());

            if (!simulate) {
                if (existing.isEmpty()) {
                    ItemStack copy = stack.copy();
                    copy.setCount(inserted);
                    inventory.setItemDirect(slot, copy);
                } else {
                    ItemStack copy = existing.copy();
                    copy.grow(inserted);
                    inventory.setItemDirect(slot, copy);
                }
            }

            if (inserted >= stack.getCount()) {
                return ItemStack.EMPTY;
            }

            ItemStack remainder = stack.copy();
            remainder.shrink(inserted);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            validateSlotIndex(slot);
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            validateSlotIndex(slot);
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            validateSlotIndex(slot);
            return inventory.isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            validateSlotIndex(slot);
            inventory.setItemDirect(slot, stack);
        }

        private void validateSlotIndex(int slot) {
            if (slot < 0 || slot >= inventory.size()) {
                throw new IllegalArgumentException("Slot " + slot + " not in valid range [0," + inventory.size() + ")");
            }
        }
    }
}
