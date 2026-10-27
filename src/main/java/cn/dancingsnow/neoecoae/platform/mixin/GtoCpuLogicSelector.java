package cn.dancingsnow.neoecoae.platform.mixin;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class GtoCpuLogicSelector {
    private static final String CLUSTER = "appeng/me/cluster/implementations/CraftingCPUCluster";
    private static final String LOGIC = "Lappeng/crafting/execution/CraftingCpuLogic;";

    private GtoCpuLogicSelector() {}

    public static boolean selects(MethodNode method) {
        if (!(method.name.contains("gto$requestPendingBlocks")
                || method.name.contains("gto$onBroadcastChanges")
                || method.name.equals("gto$isPaused")
                || method.name.equals("gto$setPaused"))) return false;
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.owner.equals(CLUSTER) && field.name.equals("craftingLogic")
                    && field.desc.equals(LOGIC)) return true;
        }
        return false;
    }
}
