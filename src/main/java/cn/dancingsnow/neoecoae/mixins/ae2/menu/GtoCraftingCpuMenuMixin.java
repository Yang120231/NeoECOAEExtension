package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.me.crafting.CraftingCPUMenu;
import cn.dancingsnow.neoecoae.compat.gto.ECOGtoCraftingCpuLogic;
import cn.dancingsnow.neoecoae.platform.mixin.RouteGtoCpuLogic;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// All mixin methods are merged before injection preparation. This bridge must outrank
// GTO's priority-1000 mixin to inject into the four methods that it adds to the menu.
@Mixin(value = CraftingCPUMenu.class, remap = false, priority = 1100)
public abstract class GtoCraftingCpuMenuMixin {
    @RouteGtoCpuLogic(at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;craftingLogic:Lappeng/crafting/execution/CraftingCpuLogic;"),
        require = 4, allow = 4)
    private CraftingCpuLogic neoecoae$gtoNativeCpu(CraftingCPUCluster cluster) {
        return ECOGtoCraftingCpuLogic.unwrap(cluster.craftingLogic);
    }
}
