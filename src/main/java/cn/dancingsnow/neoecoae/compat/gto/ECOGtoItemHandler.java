package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/** GTO exposes InternalInventory directly as IItemHandler; ECO's UI also needs explicit slot writes. */
public record ECOGtoItemHandler(InternalInventory inventory) implements IItemHandlerModifiable {
    @Override public int getSlots() { return inventory.size(); }
    @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
    @Override public void setStackInSlot(int slot, ItemStack stack) { inventory.setItemDirect(slot, stack); }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return inventory.insertItem(slot, stack, simulate);
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return inventory.extractItem(slot, amount, simulate);
    }
    @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return inventory.isItemValid(slot, stack); }
}
