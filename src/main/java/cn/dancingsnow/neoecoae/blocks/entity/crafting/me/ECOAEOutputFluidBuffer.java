package cn.dancingsnow.neoecoae.blocks.entity.crafting.me;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class ECOAEOutputFluidBuffer {
    private static final String NBT_KEY = "key";
    private static final String NBT_VALUE = "value";

    private final KeyStorage storage = new KeyStorage();
    private final Runnable changeListener;

    public ECOAEOutputFluidBuffer(Runnable changeListener) {
        this.changeListener = changeListener;
        this.storage.setOnContentsChanged(changeListener);
    }

    public KeyStorage storage() {
        return storage;
    }

    public void insertInventory(MEStorage networkInventory, IActionSource source) {
        storage.insertInventory(networkInventory, source);
    }

    public List<FluidStack> getAvailableFluids() {
        List<FluidStack> fluids = new ArrayList<>();
        for (Reference2LongMap.Entry<AEKey> entry : storage) {
            if (entry.getKey() instanceof AEFluidKey fluidKey && entry.getLongValue() > 0L) {
                fluids.add(fluidKey.toStack(saturatedInt(entry.getLongValue())));
            }
        }
        return fluids;
    }

    public FluidStack getFirstFluid() {
        for (Reference2LongMap.Entry<AEKey> entry : storage) {
            if (entry.getKey() instanceof AEFluidKey fluidKey && entry.getLongValue() > 0L) {
                return fluidKey.toStack(saturatedInt(entry.getLongValue()));
            }
        }
        return FluidStack.EMPTY;
    }

    public int fill(FluidStack stack, IFluidHandler.FluidAction action) {
        if (stack.isEmpty() || stack.getAmount() <= 0) {
            return 0;
        }
        AEFluidKey key = AEFluidKey.of(stack);
        if (action.execute()) {
            storage.lock.lock();
            try {
                storage.storage.addTo(key, stack.getAmount());
            } finally {
                storage.lock.unlock();
            }
            storage.onChanged();
        }
        return stack.getAmount();
    }

    public int drain(FluidStack stack, IFluidHandler.FluidAction action) {
        if (stack.isEmpty() || stack.getAmount() <= 0) {
            return 0;
        }
        AEFluidKey key = AEFluidKey.of(stack);
        long drained = 0L;
        storage.lock.lock();
        try {
            Iterator<Reference2LongMap.Entry<AEKey>> iterator = storage.iterator();
            while (iterator.hasNext()) {
                Reference2LongMap.Entry<AEKey> entry = iterator.next();
                AEKey storedKey = entry.getKey();
                if (!(storedKey instanceof AEFluidKey storedFluid) || !storedFluid.matches(stack)) {
                    continue;
                }
                drained = Math.min(entry.getLongValue(), stack.getAmount());
                if (action.execute()) {
                    long remaining = entry.getLongValue() - drained;
                    if (remaining <= 0L) {
                        iterator.remove();
                    } else {
                        entry.setValue(remaining);
                    }
                }
                break;
            }
        } finally {
            storage.lock.unlock();
        }
        if (drained > 0L && action.execute()) {
            storage.onChanged();
        }
        return saturatedInt(drained);
    }

    public boolean isEmpty() {
        return storage.isEmpty();
    }

    public ListTag writeToTag() {
        ListTag list = new ListTag();
        storage.lock.lock();
        try {
            for (Reference2LongMap.Entry<AEKey> entry : storage) {
                if (entry == null || entry.getKey() == null || entry.getLongValue() <= 0L) {
                    continue;
                }
                CompoundTag entryTag = new CompoundTag();
                entryTag.put(NBT_KEY, entry.getKey().toTagGeneric());
                entryTag.putLong(NBT_VALUE, entry.getLongValue());
                list.add(entryTag);
            }
        } finally {
            storage.lock.unlock();
        }
        return list;
    }

    public void readFromTag(ListTag list) {
        storage.lock.lock();
        try {
            storage.storage.clear();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                AEKey key = AEKey.fromTagGeneric(entryTag.getCompound(NBT_KEY));
                long amount = entryTag.getLong(NBT_VALUE);
                if (key != null && amount > 0L) {
                    storage.storage.put(key, amount);
                }
            }
        } finally {
            storage.lock.unlock();
        }
    }

    public void onContentsChanged() {
        changeListener.run();
    }

    private static int saturatedInt(long amount) {
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, amount);
    }
}
