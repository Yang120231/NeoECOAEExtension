package cn.dancingsnow.neoecoae.blocks.entity.crafting.me;

import appeng.api.stacks.GenericStack;
import com.gtocore.common.machine.multiblock.part.ae.slots.ExportOnlyAEFluidSlot;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public class ECOAEFluidSlot extends ExportOnlyAEFluidSlot {
    private Runnable onContentsChanged = () -> {};

    public void setOnContentsChanged(Runnable onContentsChanged) {
        this.onContentsChanged = onContentsChanged;
    }

    public Runnable getOnContentsChanged() {
        return onContentsChanged;
    }

    @Override
    public void setConfig(@Nullable GenericStack config) {
        GenericStack previous = getConfig();
        super.setConfig(config);
        if (!sameStack(previous, config)) {
            getOnContentsChanged().run();
        }
    }

    @Override
    public void setStock(@Nullable GenericStack stack) {
        GenericStack previous = getStock();
        super.setStock(stack);
        if (!sameStack(previous, stack)) {
            getOnContentsChanged().run();
        }
    }

    @Override
    public void addStack(GenericStack stack) {
        super.addStack(stack);
        getOnContentsChanged().run();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        super.setConfig(null);
        super.setStock(null);
        super.deserializeNBT(tag);
        getOnContentsChanged().run();
    }

    public long extractOrDrain(long amount, boolean simulate, boolean notify) {
        long extracted = drainCompat(amount, simulate, notify);
        if (!simulate && notify && extracted > 0L) {
            getOnContentsChanged().run();
        }
        return extracted;
    }

    private long drainCompat(long amount, boolean simulate, boolean notify) {
        try {
            Method extract = ExportOnlyAEFluidSlot.class.getMethod("extract", long.class, boolean.class, boolean.class);
            return (long) extract.invoke(this, amount, simulate, notify);
        } catch (NoSuchMethodException ignored) {
            return super.drain(amount, simulate, notify);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to drain GTCore AE fluid slot", e);
        }
    }

    private static boolean sameStack(@Nullable GenericStack left, @Nullable GenericStack right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.amount() == right.amount() && left.what().matches(right);
    }
}
