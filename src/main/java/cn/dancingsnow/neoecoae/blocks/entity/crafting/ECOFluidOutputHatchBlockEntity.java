package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.me.ECOAEOutputFluidBuffer;
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

public class ECOFluidOutputHatchBlockEntity extends AbstractCraftingBlockEntity<ECOFluidOutputHatchBlockEntity>
        implements NELDLibBlockEntityUI {

    private static final String NBT_WAITING_LIST = "neo_waiting_list";
    private static final int SYNC_INTERVAL_TICKS = 40;

    private final IActionSource actionSource = IActionSource.ofMachine(this);
    private final ECOAEOutputFluidBuffer buffer = new ECOAEOutputFluidBuffer(this::onFluidContentsChanged);
    private int syncTick;

    public ECOFluidOutputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public ModularUI createUI(Player player) {
        return NELDLibUis.createFluidOutputHatch(this, player);
    }

    public ECOAEOutputFluidBuffer getBuffer() {
        return buffer;
    }

    public boolean isMENetworkOnline() {
        return getMainNode().isOnline();
    }

    public List<FluidStack> getAvailableFluids() {
        return buffer.getAvailableFluids();
    }

    public FluidStack getFirstFluid() {
        return buffer.getFirstFluid();
    }

    public int drain(FluidStack stack, IFluidHandler.FluidAction action) {
        return buffer.drain(stack, action);
    }

    public int fill(FluidStack stack, IFluidHandler.FluidAction action) {
        if (stack.isEmpty()) {
            return 0;
        }
        var grid = getMainNode().getGrid();
        if (grid != null && getMainNode().isOnline()) {
            long accepted = grid.getStorageService()
                    .getInventory()
                    .insert(AEFluidKey.of(stack), stack.getAmount(), toActionable(action), actionSource);
            if (accepted >= stack.getAmount()) {
                return stack.getAmount();
            }
            if (accepted > 0L && action.execute()) {
                FluidStack remainder = stack.copy();
                remainder.shrink(saturatedInt(accepted));
                return saturatedInt(accepted) + buffer.fill(remainder, action);
            }
            if (action.simulate() && accepted > 0L) {
                return stack.getAmount();
            }
        }
        return buffer.fill(stack, action);
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
        if (grid == null || !getMainNode().isOnline() || buffer.isEmpty()) {
            return;
        }
        buffer.insertInventory(grid.getStorageService().getInventory(), actionSource);
    }

    private void flushInventory() {
        var grid = getMainNode().getGrid();
        if (grid != null && !buffer.isEmpty()) {
            buffer.insertInventory(grid.getStorageService().getInventory(), actionSource);
        }
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.put(NBT_WAITING_LIST, buffer.writeToTag());
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        if (data.contains(NBT_WAITING_LIST, Tag.TAG_LIST)) {
            buffer.readFromTag(data.getList(NBT_WAITING_LIST, Tag.TAG_COMPOUND));
        }
    }

    @Override
    protected void writeUiSyncTag(CompoundTag tag) {
        tag.put(NBT_WAITING_LIST, buffer.writeToTag());
    }

    @Override
    protected void readUiSyncTag(CompoundTag tag) {
        if (tag.contains(NBT_WAITING_LIST, Tag.TAG_LIST)) {
            buffer.readFromTag(tag.getList(NBT_WAITING_LIST, Tag.TAG_COMPOUND));
        }
    }

    private void onFluidContentsChanged() {
        setChanged();
        if (level != null) {
            markForUpdate();
        }
    }

    private static Actionable toActionable(IFluidHandler.FluidAction action) {
        return action.execute() ? Actionable.MODULATE : Actionable.SIMULATE;
    }

    private static int saturatedInt(long amount) {
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, amount);
    }
}
