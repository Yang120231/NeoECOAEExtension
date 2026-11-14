package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.StorageHelper;
import appeng.api.util.AECableType;
import cn.dancingsnow.neoecoae.blocks.ECOLargeIntegratedWorkingStationSmartCoolingModule;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import cn.dancingsnow.neoecoae.gui.workstation.SmartCoolingModuleUI;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.platform.ui.NEBlockEntityUI;
import cn.dancingsnow.neoecoae.platform.JdkCompat;
import cn.dancingsnow.neoecoae.platform.nbt.NEStacks;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import java.util.EnumSet;
import java.util.Set;

public class ECOLargeIntegratedWorkingStationInputHatchBlockEntity
    extends NEBlockEntity<NEIntegratedWorkingStationCluster, ECOLargeIntegratedWorkingStationInputHatchBlockEntity> implements NEBlockEntityUI {
    public static final int CAPACITY = 1_024_000;
    public static final int SMART_COOLING_CAPACITY = 16_384_000;
    public static final int SMART_COOLING_PULL_INTERVAL = 200;
    private int targetTemperature = 75;
    private FluidStack markedCoolant = FluidStack.EMPTY;
    private boolean returnUnmarkedCoolant;
    private long nextCoolantPullTick;

    public boolean isSmartCoolingModule() {
        return getBlockState().getBlock() instanceof ECOLargeIntegratedWorkingStationSmartCoolingModule;
    }

    public int getTargetTemperature() { return targetTemperature; }

    public void setTargetTemperature(int target) {
        int bounded = JdkCompat.clamp(target, 0, 99);
        if (targetTemperature != bounded) {
            targetTemperature = bounded;
            setChanged();
            markForUpdate();
            notifyController();
        }
    }

    public FluidStack getMarkedCoolant() { return markedCoolant.copy(); }

    boolean acceptsManualCoolant(FluidStack fluid) {
        return !returnUnmarkedCoolant && (markedCoolant.isEmpty() || NEStacks.sameFluid(fluid, markedCoolant));
    }

    public void setMarkedCoolant(FluidStack fluid) {
        if (!fluid.isEmpty() && !isCoolant(fluid)) return;
        FluidStack selected = fluid.isEmpty() ? FluidStack.EMPTY : NEStacks.withAmount(fluid, 1);
        if (FluidStack.areFluidStackTagsEqual(markedCoolant, selected)
            && NEStacks.sameFluid(markedCoolant, selected) && markedCoolant.getAmount() == selected.getAmount()) return;
        markedCoolant = selected;
        returnUnmarkedCoolant = selected.isEmpty() && !tank.isEmpty();
        setChanged();
        markForUpdate();
    }

    public final FluidTank tank = new FluidTank(isSmartCoolingModule() ? SMART_COOLING_CAPACITY : CAPACITY, this::isCoolant) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
            notifyController();
        }
    };

    public ECOLargeIntegratedWorkingStationInputHatchBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState, NEIntegratedWorkingStationClusterCalculator::new);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return formed && cluster != null ? cluster.getInternalConnections(this) : EnumSet.noneOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction side) {
        return AECableType.NONE;
    }

    private boolean isCoolant(FluidStack fluid) {
        if (level == null || fluid.isEmpty()) return false;
        for (var holder : level.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
            if (holder.input().ingredient().test(fluid)) return true;
        }
        return false;
    }

    private void notifyController() {
        if (getCluster() != null && getCluster().getController() != null) {
            getCluster().getController().onChangeTankFromHatch();
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        if (isSmartCoolingModule()) {
            long now = level.getGameTime();
            if (now >= nextCoolantPullTick) {
                nextCoolantPullTick = now + SMART_COOLING_PULL_INTERVAL;
                pullNetworkCoolant();
            }
            return;
        }
        var cluster = getCluster();
        for (Direction face : Direction.values()) {
            BlockPos neighborPos = pos.relative(face);
            if (cluster != null && cluster.containsBlockEntity(level.getBlockEntity(neighborPos))) {
                continue;
            }
            IFluidHandler source = cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities.find(level, neighborPos, ForgeCapabilities.FLUID_HANDLER, face.getOpposite());
            if (source != null
                && !FluidUtil.tryFluidTransfer(tank, source, tank.getCapacity(), true).isEmpty()) {
                return;
            }
        }
    }

    private void pullNetworkCoolant() {
        if (returnUnmarkedCoolant && tank.isEmpty()) {
            returnUnmarkedCoolant = false;
            setChanged();
        }
        if (markedCoolant.isEmpty() && !returnUnmarkedCoolant) return;
        var cluster = getCluster();
        if (cluster == null || cluster.getController() == null || !getMainNode().isActive()) return;
        var controller = cluster.getController();
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        var inventory = grid.getStorageService().getInventory();
        if (!tank.isEmpty() && (returnUnmarkedCoolant || !NEStacks.sameFluid(tank.getFluid(), markedCoolant))) {
            long returned = inventory.insert(AEFluidKey.of(tank.getFluid()), tank.getFluidAmount(),
                Actionable.MODULATE, IActionSource.ofMachine(this));
            if (returned < 0 || returned > tank.getFluidAmount()) throw new IllegalStateException("Invalid coolant return amount");
            if (returned > 0) tank.drain((int) returned, IFluidHandler.FluidAction.EXECUTE);
            if (!tank.isEmpty()) return;
        }
        if (returnUnmarkedCoolant) {
            returnUnmarkedCoolant = false;
            setChanged();
        }
        if (markedCoolant.isEmpty() || !controller.isActiveCooling() || tank.getSpace() <= 0) return;
        int threshold = JdkCompat.clamp(cn.dancingsnow.neoecoae.config.NEConfig.smartCoolingRefillThresholdPercent, 1, 100);
        if ((long) tank.getFluidAmount() * 100 >= (long) tank.getCapacity() * threshold) return;
        AEFluidKey selected = AEFluidKey.of(markedCoolant);
        int space = tank.fill(selected.toStack(tank.getSpace()), IFluidHandler.FluidAction.SIMULATE);
        if (space <= 0) return;
        long extracted = StorageHelper.poweredExtraction(grid.getEnergyService(), inventory, selected,
            space, IActionSource.ofMachine(this));
        if (extracted < 0 || extracted > space) throw new IllegalStateException("Invalid coolant extraction amount");
        if (extracted > 0) tank.fill(selected.toStack((int) extracted), IFluidHandler.FluidAction.EXECUTE);
    }

    public ModularUI createUI(Player player) {
        if (isSmartCoolingModule()) return SmartCoolingModuleUI.create(this, player).nativeUI(this);
        return FluidHatchUI.createFluidHatchUI(
            this, player, tank, "block.neoecoae.large_integrated_working_station_input_hatch", true, true);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        tank.writeToNBT(data);
        data.putInt("targetTemperature", targetTemperature);
        data.put("markedCoolant", markedCoolant.writeToNBT(new CompoundTag()));
        data.putBoolean("returnUnmarkedCoolant", returnUnmarkedCoolant);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        tank.readFromNBT(data);
        targetTemperature = data.contains("targetTemperature") ? JdkCompat.clamp(data.getInt("targetTemperature"), 0, 99) : 75;
        markedCoolant = FluidStack.loadFluidStackFromNBT(data.getCompound("markedCoolant"));
        if (!markedCoolant.isEmpty()) markedCoolant.setAmount(1);
        returnUnmarkedCoolant = data.contains("returnUnmarkedCoolant") ? data.getBoolean("returnUnmarkedCoolant")
            : markedCoolant.isEmpty() && !tank.isEmpty();
        nextCoolantPullTick = 0;
    }
}
