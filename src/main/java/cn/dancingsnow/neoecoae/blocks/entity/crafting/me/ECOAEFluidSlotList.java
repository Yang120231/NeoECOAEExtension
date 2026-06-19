package cn.dancingsnow.neoecoae.blocks.entity.crafting.me;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlotList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class ECOAEFluidSlotList implements IConfigurableSlotList {
    public static final int CONFIG_SIZE = 16;

    private static final String NBT_SLOT = "Slot";
    private static final String NBT_DATA = "Data";

    private final ECOAEFluidSlot[] inventory = new ECOAEFluidSlot[CONFIG_SIZE];
    private final Runnable changeListener;

    public ECOAEFluidSlotList(Runnable changeListener) {
        this.changeListener = changeListener;
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = new ECOAEFluidSlot();
            inventory[i].setOnContentsChanged(changeListener);
        }
    }

    public ECOAEFluidSlot[] getInventory() {
        return inventory;
    }

    public void syncME(MEStorage networkInventory, IActionSource source) {
        for (ECOAEFluidSlot slot : inventory) {
            GenericStack exceed = slot.exceedStack();
            if (exceed != null && exceed.amount() > 0L) {
                long inserted = networkInventory.insert(exceed.what(), exceed.amount(), Actionable.MODULATE, source);
                if (inserted > 0L) {
                    slot.extractOrDrain(inserted, false, true);
                    continue;
                }
            }

            GenericStack requested = slot.requestStack();
            if (requested == null || requested.amount() <= 0L) {
                continue;
            }
            long extracted =
                    networkInventory.extract(requested.what(), requested.amount(), Actionable.MODULATE, source);
            if (extracted > 0L) {
                slot.addStack(new GenericStack(requested.what(), extracted));
            }
        }
    }

    public List<FluidStack> getAvailableFluids() {
        List<FluidStack> fluids = new ArrayList<>();
        for (ECOAEFluidSlot slot : inventory) {
            GenericStack stock = slot.getStock();
            if (stock == null || stock.amount() <= 0L || !(stock.what() instanceof AEFluidKey fluidKey)) {
                continue;
            }
            fluids.add(fluidKey.toStack(saturatedInt(stock.amount())));
        }
        return fluids;
    }

    @Override
    public boolean hasStackInConfig(GenericStack stack, boolean checkExternal) {
        if (stack == null) {
            return false;
        }
        for (ECOAEFluidSlot slot : inventory) {
            GenericStack config = slot.getConfig();
            if (config != null && config.what().matches(stack)) {
                return true;
            }
        }
        return false;
    }

    public FluidStack getFirstFluid() {
        for (ECOAEFluidSlot slot : inventory) {
            GenericStack stock = slot.getStock();
            if (stock != null && stock.amount() > 0L && stock.what() instanceof AEFluidKey fluidKey) {
                return fluidKey.toStack(saturatedInt(stock.amount()));
            }
        }
        return FluidStack.EMPTY;
    }

    public int drain(FluidStack stack, IFluidHandler.FluidAction action) {
        if (stack.isEmpty()) {
            return 0;
        }
        long remaining = stack.getAmount();
        long drained = 0L;
        for (ECOAEFluidSlot slot : inventory) {
            if (remaining <= 0L) {
                break;
            }
            FluidStack request = stack.copy();
            request.setAmount(saturatedInt(remaining));
            FluidStack slotDrained = slot.drain(request, action);
            if (!slotDrained.isEmpty()) {
                drained += slotDrained.getAmount();
                remaining -= slotDrained.getAmount();
            }
        }
        return saturatedInt(drained);
    }

    public ListTag writeToTag() {
        ListTag list = new ListTag();
        for (int i = 0; i < inventory.length; i++) {
            CompoundTag slotTag = inventory[i].serializeNBT();
            if (slotTag.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putByte(NBT_SLOT, (byte) i);
            entry.put(NBT_DATA, slotTag);
            list.add(entry);
        }
        return list;
    }

    public void readFromTag(ListTag list) {
        for (ECOAEFluidSlot slot : inventory) {
            slot.deserializeNBT(new CompoundTag());
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slotIndex = Byte.toUnsignedInt(entry.getByte(NBT_SLOT));
            if (slotIndex >= 0 && slotIndex < inventory.length && entry.contains(NBT_DATA, Tag.TAG_COMPOUND)) {
                inventory[slotIndex].deserializeNBT(entry.getCompound(NBT_DATA));
            }
        }
        changeListener.run();
    }

    @Override
    public IConfigurableSlot getConfigurableSlot(int index) {
        return inventory[index];
    }

    @Override
    public int getConfigurableSlots() {
        return inventory.length;
    }

    public boolean isAutoPull() {
        return false;
    }

    public boolean isStocking() {
        return false;
    }

    private static int saturatedInt(long amount) {
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, amount);
    }
}
