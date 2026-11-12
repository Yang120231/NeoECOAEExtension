package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.gtocore.common.data.GTOItems;

/**
 * GTO 0.5.6 订单物品识别。
 *
 * <p>与 GTO {@code OptimizedCraftingCpuLogic} 保持一致：最终产物为订单或临时订单时，
 * 订单物品本身不会作为实体产物回到网络，CPU 需要按发出的订单样板判定完成。</p>
 */
public final class ECOGtoOrders {
    private ECOGtoOrders() {}

    /** 最终产物是否为 GTO 订单或临时订单。 */
    public static boolean isOrder(GenericStack output) {
        return output != null && (isItem(output.what(), false) || isItem(output.what(), true));
    }

    /** 最终产物是否为多物品下单生成的临时订单。 */
    public static boolean isTemporaryOrder(GenericStack output) {
        return output != null && isItem(output.what(), true);
    }

    /** 样板是否产出临时订单，即 GTO 为多物品下单生成的临时样板。 */
    public static boolean producesTemporaryOrder(IPatternDetails pattern) {
        try {
            for (var output : pattern.getOutputs()) {
                if (output != null && isItem(output.what(), true)) return true;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private static boolean isItem(AEKey key, boolean temporary) {
        if (!(key instanceof AEItemKey item)) return false;
        var entry = temporary ? GTOItems.TEMP_ORDER : GTOItems.ORDER;
        return entry != null && item.getItem() == entry.get();
    }
}
