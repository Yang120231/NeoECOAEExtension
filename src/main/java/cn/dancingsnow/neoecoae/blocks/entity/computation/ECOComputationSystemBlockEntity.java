package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOComputationHost;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.api.me.ElapsedTimeTracker;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.multiblock.BuildPreviewState;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibBlockEntityUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class ECOComputationSystemBlockEntity extends AbstractComputationBlockEntity<ECOComputationSystemBlockEntity>
        implements INEMultiblockBuildHost, NELDLibBlockEntityUI, IECOComputationHost {

    @Getter
    private final IECOTier tier;

    private int usedThread;
    private int totalThread;
    private int parallelCount;
    private long availableBytes;
    private long totalBytes;
    private long usedBytes;
    /** Sum of CPU accelerators from all parallel cores in the cluster. */
    private int acceleratorCount;

    /** CPU auto-selection mode, persisted in the controller's NBT. */
    private CpuSelectionMode cpuSelectionMode = CpuSelectionMode.ANY;

    private boolean computationStatsDirty = true;

    public static final int REQUIRED_INFINITE_STORAGE_COMPONENTS = 64;
    private static final ResourceLocation INFINITE_STORAGE_COMPONENT_ID =
            ResourceLocation.fromNamespaceAndPath("gtlcore", "infinite_cell_component");
    private static final String NBT_INFINITE_STORAGE_COMPONENT = "infiniteStorageComponent";

    private final ItemStackHandler infiniteStorageComponent = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            onInfiniteStorageComponentChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && canUseInfiniteStorageComponents() && isInfiniteStorageComponent(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return canUseInfiniteStorageComponents() ? REQUIRED_INFINITE_STORAGE_COMPONENTS : 0;
        }
    };

    /** Shared preview/build state, delegates NBT sync to {@link BuildPreviewState}. */
    private final BuildPreviewState buildPreview = new BuildPreviewState();

    private long uiRevision = 0L;

    public ECOComputationSystemBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState blockState, IECOTier tier) {
        super(type, pos, blockState);
        this.tier = tier;
        getMainNode().addService(IECOComputationHost.class, this);
    }

    @Override
    public ECOComputationSystemBlockEntity getComputationHost() {
        return this;
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (updateExposed) {
            markComputationStatsDirty();
            updateInfos();
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        markUiStateDirty();
        if (reason != IGridNodeListener.State.GRID_BOOT
                && cluster != null
                && getMainNode().isActive()) {
            cluster.updateGridForChangedCpu(cluster);
        }
    }

    private void recalculateComputationStats() {
        if (cluster != null) {
            availableBytes = cluster.getAvailableStorage();
            totalBytes = cluster.getTotalStorageBytes();
            usedBytes = cluster.getUsedStorageBytes();
            usedThread = cluster.getActiveCpuCountCached();
            totalThread = cluster.getMaxThreads();
            parallelCount = cluster.getParallelCores().size();
            acceleratorCount = cluster.getCPUAccelerators();
        } else {
            usedThread = 0;
            totalThread = 0;
            parallelCount = 0;
            availableBytes = 0;
            totalBytes = 0;
            usedBytes = 0;
            acceleratorCount = 0;
        }
    }

    /**
     * Marks the cached computation stats (thread/byte/accelerator counts)
     * as stale and increments the UI revision to trigger a menu state resync.
     * Call this when the multiblock cluster changes or threading cores update.
     */
    public void markComputationStatsDirty() {
        computationStatsDirty = true;
        markUiStateDirty();
    }

    /** Returns a monotonically increasing revision for UI state duplicate suppression. */
    public long getUiRevision() {
        return uiRevision;
    }

    /** Increments the UI revision so the next menu tick will push a fresh state. */
    private void markUiStateDirty() {
        uiRevision++;
    }

    private void ensureStatsCurrent() {
        if (!computationStatsDirty) {
            return;
        }
        recalculateComputationStats();
        computationStatsDirty = false;
    }

    public void updateInfos() {
        ensureStatsCurrent();
        setChanged();
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        tickBuild(level);
    }

    public int getUsedThread() {
        ensureStatsCurrent();
        return usedThread;
    }

    public boolean isFormed() {
        return formed;
    }

    public boolean isRunning() {
        return getUsedThread() > 0;
    }

    public int getTotalThread() {
        ensureStatsCurrent();
        return totalThread;
    }

    public int getParallelCount() {
        ensureStatsCurrent();
        return parallelCount;
    }

    public long getAvailableBytes() {
        ensureStatsCurrent();
        return isInfiniteStorageUnlocked() ? Long.MAX_VALUE : availableBytes;
    }

    public long getTotalBytes() {
        ensureStatsCurrent();
        return isInfiniteStorageUnlocked() ? Long.MAX_VALUE : totalBytes;
    }

    public long getUsedBytes() {
        ensureStatsCurrent();
        return Math.max(0L, usedBytes);
    }

    public int getAcceleratorCount() {
        ensureStatsCurrent();
        return acceleratorCount;
    }

    public CpuSelectionMode getCpuSelectionMode() {
        return cpuSelectionMode;
    }

    public IItemHandlerModifiable getInfiniteStorageComponentInventory() {
        return infiniteStorageComponent;
    }

    public int getInfiniteStorageComponentCount() {
        if (!canUseInfiniteStorageComponents()) {
            return 0;
        }
        ItemStack stack = infiniteStorageComponent.getStackInSlot(0);
        return isInfiniteStorageComponent(stack) ? stack.getCount() : 0;
    }

    public boolean isInfiniteStorageUnlocked() {
        return tier.getTier() >= ECOTier.L9.getTier()
                && getInfiniteStorageComponentCount() >= REQUIRED_INFINITE_STORAGE_COMPONENTS;
    }

    public boolean canUseInfiniteStorageComponents() {
        return tier.getTier() == ECOTier.L9.getTier() && isInfiniteStorageComponentAvailable();
    }

    public static boolean isInfiniteStorageComponentAvailable() {
        return BuiltInRegistries.ITEM.get(INFINITE_STORAGE_COMPONENT_ID) != Items.AIR;
    }

    private void onInfiniteStorageComponentChanged() {
        if (cluster != null) {
            cluster.recalculateRemainingStorage();
            cluster.updateGridForChangedCpu(cluster);
        }
        setChanged();
        markComputationStatsDirty();
    }

    private static boolean isInfiniteStorageComponent(ItemStack stack) {
        Item component = BuiltInRegistries.ITEM.get(INFINITE_STORAGE_COMPONENT_ID);
        return component != Items.AIR && stack.is(component);
    }

    public void setCpuSelectionMode(CpuSelectionMode mode) {
        this.cpuSelectionMode = mode;
        setChanged();
        markUiStateDirty();
    }

    /**
     * Creates a snapshot of current computation stats for S2C UI sync.
     * <p>
     * This reads cached stats. Mutating cluster paths mark the cache dirty and
     * update it before bumping the UI revision.
     * </p>
     */
    public NEComputationUiState createComputationUiState() {
        ensureStatsCurrent();
        CpuSelectionMode mode = cluster != null ? cluster.getSelectionMode() : cpuSelectionMode;
        return new NEComputationUiState(
                worldPosition,
                formed,
                cluster != null && cluster.isActive(),
                usedThread,
                totalThread,
                getAvailableBytes(),
                getTotalBytes(),
                getUsedBytes(),
                parallelCount,
                acceleratorCount,
                mode,
                collectComputationRecipeEntries());
    }

    private List<NECraftingRecipeUiEntry> collectComputationRecipeEntries() {
        if (cluster == null) {
            return List.of();
        }
        List<ECOCraftingCPU> activeCpus = cluster.getActiveCPUs();
        if (activeCpus.isEmpty()) {
            return List.of();
        }
        List<NECraftingRecipeUiEntry> entries = new ArrayList<>(activeCpus.size());
        int index = 0;
        for (ECOCraftingCPU cpu : activeCpus) {
            NECraftingRecipeUiEntry entry = createComputationRecipeEntry(cpu, index);
            if (entry != null) {
                entries.add(entry);
            }
            index++;
        }
        return List.copyOf(entries);
    }

    @Nullable private NECraftingRecipeUiEntry createComputationRecipeEntry(ECOCraftingCPU cpu, int index) {
        if (cpu == null) {
            return null;
        }
        ECOCraftingCPULogic logic = cpu.getLogic();
        if (!logic.hasJob()) {
            return null;
        }
        GenericStack finalOutput = logic.getFinalJobOutput();
        if (finalOutput == null || finalOutput.amount() <= 0 || !(finalOutput.what() instanceof AEItemKey itemKey)) {
            return null;
        }
        ItemStack output = itemKey.toStack(1);
        if (output.isEmpty()) {
            return null;
        }
        ElapsedTimeTracker tracker = logic.getElapsedTimeTracker();
        long total = Math.max(1L, tracker.getSyntheticStartItemCount());
        long remaining = Math.max(0L, Math.min(total, tracker.getSyntheticRemainingItemCount()));
        NECraftingRecipeUiEntry.Status status = logic.isCantStoreItems() || logic.isJobSuspended()
                ? NECraftingRecipeUiEntry.Status.WAITING_OUTPUT
                : NECraftingRecipeUiEntry.Status.RUNNING;
        return new NECraftingRecipeUiEntry(
                computationTaskId(cpu, finalOutput, index), output, finalOutput.amount(), 1L, total, remaining, status);
    }

    private static String computationTaskId(ECOCraftingCPU cpu, GenericStack output, int index) {
        BlockPos ownerPos = cpu.getOwner() != null ? cpu.getOwner().getBlockPos() : null;
        String owner = ownerPos != null ? Long.toString(ownerPos.asLong()) : "proxy";
        return "cpu:" + owner + ":" + index + ":" + output.what().hashCode();
    }

    @Override
    public ModularUI createUI(Player player) {
        return NELDLibUis.createComputationController(this, player);
    }

    // getPreviewStatusComponent() is provided by INEMultiblockBuildHost default

    // INEMultiblockBuildHost implementation

    @Override
    public BlockPos getHostPos() {
        return worldPosition;
    }

    @Override
    public BlockState getHostBlockState() {
        return getBlockState();
    }

    @Override
    public MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getComputationSystemDefinition(tier);
    }

    @Deprecated
    @Override
    public void previewStructure(ServerPlayer player) {
        previewStructure(player, false);
    }

    @Deprecated
    @Override
    public void autoBuild(ServerPlayer player) {
        autoBuild(player, false);
    }

    public int getPreviewMissingBlocks() {
        return buildPreview.previewMissingBlocks;
    }

    public int getPreviewConflictBlocks() {
        return buildPreview.previewConflictBlocks;
    }

    public int getPreviewReusedBlocks() {
        return buildPreview.previewReusedBlocks;
    }

    public int getPreviewRequiredItems() {
        return buildPreview.previewRequiredItems;
    }

    // Multi-block builder methods invoked by LDLib UI actions.

    // increaseBuildLength / decreaseBuildLength are provided by INEMultiblockBuildHost default

    @Override
    public BuildPreviewState getBuildPreview() {
        return buildPreview;
    }

    @Override
    public void markPreviewDirty() {
        setChanged();
        markUiStateDirty();
    }

    // buildPreviewStatusComponent() is provided by INEMultiblockBuildHost default

    // NBT persistence
    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("selectedBuildLength", getSelectedBuildLength());
        tag.putInt("cpuSelectionMode", cpuSelectionMode.ordinal());
        tag.put(NBT_INFINITE_STORAGE_COMPONENT, infiniteStorageComponent.serializeNBT());
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        buildPreview.selectedBuildLength = Math.max(1, tag.getInt("selectedBuildLength"));
        if (tag.contains("cpuSelectionMode")) {
            int ordinal = tag.getInt("cpuSelectionMode");
            CpuSelectionMode[] values = CpuSelectionMode.values();
            if (ordinal >= 0 && ordinal < values.length) {
                cpuSelectionMode = values[ordinal];
            }
        }
        if (tag.contains(NBT_INFINITE_STORAGE_COMPONENT)) {
            infiniteStorageComponent.deserializeNBT(tag.getCompound(NBT_INFINITE_STORAGE_COMPONENT));
        }
        buildPreview.buildInProgress = false;
        buildPreview.resetPreview(BuildPreviewState.DEFAULT_STATUS_KEY);
    }

    // UI sync (Layer 1: chunk-load NBT)
    // getUpdateTag/handleUpdateTag/getUpdatePacket are provided by NEBlockEntity.

    @Override
    protected void writeUiSyncTag(CompoundTag tag) {
        tag.putInt("neo_usedThread", usedThread);
        tag.putInt("neo_totalThread", totalThread);
        tag.putInt("neo_parallelCount", parallelCount);
        tag.putLong("neo_availableBytes", availableBytes);
        tag.putLong("neo_totalBytes", totalBytes);
        tag.putLong("neo_usedBytes", usedBytes);
        buildPreview.writeToTag(tag);
    }

    @Override
    protected void readUiSyncTag(CompoundTag tag) {
        if (tag.contains("neo_usedThread")) usedThread = tag.getInt("neo_usedThread");
        if (tag.contains("neo_totalThread")) totalThread = tag.getInt("neo_totalThread");
        if (tag.contains("neo_parallelCount")) parallelCount = tag.getInt("neo_parallelCount");
        if (tag.contains("neo_availableBytes")) availableBytes = tag.getLong("neo_availableBytes");
        if (tag.contains("neo_totalBytes")) totalBytes = tag.getLong("neo_totalBytes");
        if (tag.contains("neo_usedBytes")) usedBytes = tag.getLong("neo_usedBytes");
        buildPreview.readFromTag(tag);
    }
}
