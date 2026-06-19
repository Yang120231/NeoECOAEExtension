package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.me.ECOAEFluidSlotList;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibBlockEntityUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class ECOFluidInputHatchBlockEntity extends AbstractCraftingBlockEntity<ECOFluidInputHatchBlockEntity>
        implements NELDLibBlockEntityUI {

    private static final String NBT_FLUID_SLOTS = "neo_fluid_slots";
    private static final int SYNC_INTERVAL_TICKS = 40;

    private final IActionSource actionSource = IActionSource.ofMachine(this);
    private final ECOAEFluidSlotList fluids = new ECOAEFluidSlotList(this::onFluidContentsChanged);
    private int syncTick;

    public ECOFluidInputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public ModularUI createUI(Player player) {
        return NELDLibUis.createFluidInputHatch(this, player);
    }

    public ECOAEFluidSlotList getFluids() {
        return fluids;
    }

    public boolean isMENetworkOnline() {
        return getMainNode().isOnline();
    }

    public List<FluidStack> getAvailableFluids() {
        return fluids.getAvailableFluids();
    }

    public FluidStack getFirstFluid() {
        return fluids.getFirstFluid();
    }

    public int drain(FluidStack stack, IFluidHandler.FluidAction action) {
        return fluids.drain(stack, action);
    }

    @Override
    public void setRemoved() {
        flushInventory();
        super.setRemoved();
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide || ++syncTick < SYNC_INTERVAL_TICKS) {
            return;
        }
        syncTick = 0;
        var grid = getMainNode().getGrid();
        if (grid == null || !getMainNode().isOnline()) {
            return;
        }
        fluids.syncME(grid.getStorageService().getInventory(), actionSource);
    }

    private void flushInventory() {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            for (var slot : fluids.getInventory()) {
                var stock = slot.getStock();
                if (stock != null && stock.amount() > 0L) {
                    long inserted = grid.getStorageService()
                            .getInventory()
                            .insert(stock.what(), stock.amount(), Actionable.MODULATE, actionSource);
                    if (inserted > 0L) {
                        slot.extractOrDrain(inserted, false, true);
                    }
                }
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.put(NBT_FLUID_SLOTS, fluids.writeToTag());
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        if (data.contains(NBT_FLUID_SLOTS, Tag.TAG_LIST)) {
            fluids.readFromTag(data.getList(NBT_FLUID_SLOTS, Tag.TAG_COMPOUND));
        }
    }

    @Override
    protected void writeUiSyncTag(CompoundTag tag) {
        tag.put(NBT_FLUID_SLOTS, fluids.writeToTag());
    }

    @Override
    protected void readUiSyncTag(CompoundTag tag) {
        if (tag.contains(NBT_FLUID_SLOTS, Tag.TAG_LIST)) {
            fluids.readFromTag(tag.getList(NBT_FLUID_SLOTS, Tag.TAG_COMPOUND));
        }
    }

    private void onFluidContentsChanged() {
        setChanged();
        if (level != null) {
            markForUpdate();
        }
    }
}
