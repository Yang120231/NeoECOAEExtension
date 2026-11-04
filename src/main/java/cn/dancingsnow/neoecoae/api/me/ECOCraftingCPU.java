package cn.dancingsnow.neoecoae.api.me;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimSink;

/** CPU contract shared by ECO execution, menus and physical-output delivery. */
public abstract class ECOCraftingCPU implements ICraftingCPU {
    public abstract IGrid getGrid();
    public abstract ECOCraftingOutputClaimSink getOutputClaimSink();
    public abstract void markDirty();
    public abstract ECOCraftingCPULogic getLogic();
}
