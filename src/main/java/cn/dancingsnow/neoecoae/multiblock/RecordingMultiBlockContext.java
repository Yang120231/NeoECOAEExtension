package cn.dancingsnow.neoecoae.multiblock;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * A non-world-modifying {@link MultiBlockContext} that only records
 * block positions and required items without actually placing blocks
 * in the real level.
 */
public final class RecordingMultiBlockContext extends MultiBlockContext {

    private final List<ItemStack> requiredItems = new ArrayList<>();
    private final List<BlockPos> recordedBlocks = new ArrayList<>();
    private final Level level;

    private RecordingMultiBlockContext(int repeats, Level level) {
        this.repeats = repeats;
        this.level = level;
    }

    /**
     * Creates a recording context by running the host's build definition
     * at the given length, capturing all block placements without modifying
     * the world.
     */
    public static RecordingMultiBlockContext record(
            INEMultiblockBuildHost host, Level level, BlockPos hostPos, int length) {
        RecordingMultiBlockContext ctx = new RecordingMultiBlockContext(length, level);
        host.getBuildDefinition().createLevel(ctx);
        return ctx;
    }

    @Override
    public void setBlock(BlockPos pos, BlockState blockState) {
        ItemStack item = blockState.getBlock().asItem().getDefaultInstance();
        if (!item.isEmpty()) {
            addRequired(item);
        }
        recordedBlocks.add(pos);
        // Deliberately skip level.setBlock — recording only
    }

    @Override
    public void setBlockEntity(BlockPos pos, BiFunction<BlockPos, BlockState, BlockEntity> sup) {
        // Recording only — do not write to world.
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public List<BlockPos> allBlocks() {
        return recordedBlocks;
    }

    @Override
    public boolean isFormed() {
        return false;
    }

    public List<ItemStack> getRequiredItems() {
        return requiredItems;
    }

    private void addRequired(ItemStack itemStack) {
        if (itemStack.isEmpty()) return;
        for (ItemStack stack : requiredItems) {
            if (ItemStack.isSameItemSameTags(itemStack, stack)) {
                if (stack.getCount() + itemStack.getCount() <= stack.getMaxStackSize()) {
                    stack.setCount(stack.getCount() + itemStack.getCount());
                }
                return;
            }
        }
        requiredItems.add(itemStack);
    }
}
