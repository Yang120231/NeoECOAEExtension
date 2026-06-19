package cn.dancingsnow.neoecoae.compat.gtocore;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gtocore.common.data.machines.GTAEMachines;
import com.gtocore.common.machine.multiblock.part.ae.MEInputHatchPartMachine;
import com.gtocore.common.machine.multiblock.part.ae.MEOutputHatchPartMachine;
import com.gtocore.common.machine.multiblock.part.ae.MEStockingHatchPartMachine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class GTOMECraftingHatches {
    private GTOMECraftingHatches() {}

    public static boolean isFluidInputHatch(BlockState state) {
        return state.is(GTAEMachines.FLUID_IMPORT_HATCH_ME.get())
                || state.is(GTAEMachines.STOCKING_IMPORT_HATCH_ME.get())
                || state.is(NEBlocks.INPUT_HATCH.get());
    }

    public static boolean isFluidOutputHatch(BlockState state) {
        return state.is(GTAEMachines.FLUID_EXPORT_HATCH_ME.get()) || state.is(NEBlocks.OUTPUT_HATCH.get());
    }

    public static BlockState defaultInputState() {
        return GTAEMachines.FLUID_IMPORT_HATCH_ME.defaultBlockState();
    }

    public static BlockState defaultOutputState() {
        return GTAEMachines.FLUID_EXPORT_HATCH_ME.defaultBlockState();
    }

    public static boolean hasValidInputPort(Level level, BlockPos pos) {
        return inputPort(level, pos) != null;
    }

    public static boolean hasValidOutputPort(Level level, BlockPos pos) {
        return outputPort(level, pos) != null;
    }

    @Nullable public static FluidPort inputPort(Level level, @Nullable BlockPos pos) {
        if (pos == null) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof ECOFluidInputHatchBlockEntity hatch) {
            return new ECOInputFluidPort(hatch);
        }
        NotifiableFluidTank tank = findGTOFluidTank(level, pos, IO.IN, true);
        return tank == null ? null : new GTFluidPort(tank);
    }

    @Nullable public static FluidPort outputPort(Level level, @Nullable BlockPos pos) {
        if (pos == null) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof ECOFluidOutputHatchBlockEntity hatch) {
            return new ECOOutputFluidPort(hatch);
        }
        NotifiableFluidTank tank = findGTOFluidTank(level, pos, IO.OUT, false);
        return tank == null ? null : new GTFluidPort(tank);
    }

    @Nullable private static NotifiableFluidTank findGTOFluidTank(Level level, BlockPos pos, IO io, boolean input) {
        if (!(level.getBlockEntity(pos) instanceof MetaMachineBlockEntity blockEntity)) {
            return null;
        }
        MetaMachine machine = blockEntity.getMetaMachine();
        if (machine == null || !isExpectedGTOHatch(machine, input)) {
            return null;
        }
        for (MachineTrait trait : machine.getTraits()) {
            if (trait instanceof NotifiableFluidTank tank && supportsIO(tank, io)) {
                return tank;
            }
        }
        return null;
    }

    private static boolean isExpectedGTOHatch(MetaMachine machine, boolean input) {
        if (input) {
            return machine instanceof MEInputHatchPartMachine
                    || machine instanceof MEStockingHatchPartMachine
                    || machine.getDefinition() == GTAEMachines.FLUID_IMPORT_HATCH_ME
                    || machine.getDefinition() == GTAEMachines.STOCKING_IMPORT_HATCH_ME;
        }
        return machine instanceof MEOutputHatchPartMachine
                || machine.getDefinition() == GTAEMachines.FLUID_EXPORT_HATCH_ME;
    }

    private static boolean supportsIO(NotifiableFluidTank tank, IO io) {
        IO handlerIO = tank.getHandlerIO();
        IO capabilityIO = tank.getCapabilityIO();
        return (handlerIO != null && handlerIO.support(io)) || (capabilityIO != null && capabilityIO.support(io));
    }

    public interface FluidPort {
        List<FluidStack> getAvailableFluids();

        FluidStack getFirstFluid();

        int drain(FluidStack stack, IFluidHandler.FluidAction action);

        int fill(FluidStack stack, IFluidHandler.FluidAction action);
    }

    private record ECOInputFluidPort(ECOFluidInputHatchBlockEntity hatch) implements FluidPort {
        @Override
        public List<FluidStack> getAvailableFluids() {
            return hatch.getAvailableFluids();
        }

        @Override
        public FluidStack getFirstFluid() {
            return hatch.getFirstFluid();
        }

        @Override
        public int drain(FluidStack stack, IFluidHandler.FluidAction action) {
            return hatch.drain(stack, action);
        }

        @Override
        public int fill(FluidStack stack, IFluidHandler.FluidAction action) {
            return 0;
        }
    }

    private record ECOOutputFluidPort(ECOFluidOutputHatchBlockEntity hatch) implements FluidPort {
        @Override
        public List<FluidStack> getAvailableFluids() {
            return hatch.getAvailableFluids();
        }

        @Override
        public FluidStack getFirstFluid() {
            return hatch.getFirstFluid();
        }

        @Override
        public int drain(FluidStack stack, IFluidHandler.FluidAction action) {
            return hatch.drain(stack, action);
        }

        @Override
        public int fill(FluidStack stack, IFluidHandler.FluidAction action) {
            return hatch.fill(stack, action);
        }
    }

    private record GTFluidPort(NotifiableFluidTank tank) implements FluidPort {
        @Override
        public List<FluidStack> getAvailableFluids() {
            List<FluidStack> fluids = new ArrayList<>();
            tank.fastForEachFluids((fluid, amount) -> {
                if (!fluid.isEmpty() && amount > 0L) {
                    FluidStack copy = fluid.copy();
                    copy.setAmount(saturatedInt(amount));
                    fluids.add(copy);
                }
            });
            return fluids;
        }

        @Override
        public FluidStack getFirstFluid() {
            List<FluidStack> fluids = getAvailableFluids();
            return fluids.isEmpty() ? FluidStack.EMPTY : fluids.get(0).copy();
        }

        @Override
        public int drain(FluidStack stack, IFluidHandler.FluidAction action) {
            if (stack.isEmpty()) {
                return 0;
            }
            return tank.drainInternal(stack, action).getAmount();
        }

        @Override
        public int fill(FluidStack stack, IFluidHandler.FluidAction action) {
            return stack.isEmpty() ? 0 : tank.fillInternal(stack, action);
        }
    }

    private static int saturatedInt(long amount) {
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, amount);
    }
}
