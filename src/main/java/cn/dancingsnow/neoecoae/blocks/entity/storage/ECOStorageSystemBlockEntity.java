package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.helpers.IPriorityHost;
import appeng.menu.ISubMenu;
import appeng.util.SettingsFrom;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibBlockEntityUI;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiMatrixState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import cn.dancingsnow.neoecoae.impl.storage.ECOHostDomainStorage;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellMetadata;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageSavedData;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.multiblock.BuildPreviewState;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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

public class ECOStorageSystemBlockEntity extends AbstractStorageBlockEntity<ECOStorageSystemBlockEntity>
        implements IGridTickable, IStorageProvider, INEMultiblockBuildHost, IPriorityHost, NELDLibBlockEntityUI {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int REQUIRED_INFINITE_STORAGE_COMPONENTS = 64;
    private static final long INFINITE_STORAGE_CAPACITY_THRESHOLD = 1_000_000_000_000L;
    private static final int STORAGE_INTERFACE_EXPORT_KEYS_PER_TICK = 64;
    private static final int MIGRATION_NOT_STARTED = 0;
    private static final int MIGRATION_COPYING = 1;
    private static final int MIGRATION_COMMITTED_TO_DOMAIN = 2;
    private static final int MIGRATION_SOURCE_CLEARED = 3;
    private static final int MIGRATION_BOUND_AS_MEMBER = 4;
    private static final ResourceLocation INFINITE_STORAGE_COMPONENT_ID =
            ResourceLocation.fromNamespaceAndPath("gtlcore", "infinite_cell_component");
    private static final String NBT_INFINITE_STORAGE_COMPONENT = "infiniteStorageComponent";
    private static final String NBT_HOST_MODE = "hostMode";
    private static final String NBT_HOST_DOMAIN_ID = "hostDomainId";
    private static final String NBT_MEMBER_DISKS = "memberDiskIds";
    private static final String NBT_MIGRATION_STEPS = "migrationSteps";
    private static final String NBT_DOMAIN_KEY_TYPES = "domainKeyTypes";

    @Getter
    private final IECOTier tier;

    private long[] usedTypes;
    private long[] totalTypes;
    private long[] usedBytes;
    private long[] totalBytes;
    private boolean storageStatsDirty = true;
    private ECOStorageHostMode hostMode = ECOStorageHostMode.UNFORMED;

    @Nullable private UUID hostDomainId;

    private final List<UUID> memberDiskIds = new ArrayList<>();
    private final Map<UUID, Integer> migrationSteps = new LinkedHashMap<>();
    private final Set<Byte> domainKeyTypes = new HashSet<>();

    @Nullable private ECOHostDomainStorage hostDomainStorage;

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
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == 0 && isInfiniteStorageUnlocked()) {
                if (!canExitInfiniteMode()) {
                    return ItemStack.EMPTY;
                }
                if (!simulate) {
                    exitInfiniteMode();
                }
            }
            return super.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return canUseInfiniteStorageComponents() ? REQUIRED_INFINITE_STORAGE_COMPONENTS : 0;
        }
    };

    /** Storage priority for AE2 network insertion/extraction ordering. */
    private int priority = 0;

    /** Shared preview/build state, delegates NBT sync to {@link BuildPreviewState}. */
    private final BuildPreviewState buildPreview = new BuildPreviewState();

    private long storedEnergy;
    private long maxEnergy;
    private int selectedBuildLength = 1;
    private int previewMissingBlocks;
    private int previewConflictBlocks;
    private int previewReusedBlocks;
    private int previewRequiredItems;
    private String previewStatusKey = "gui.neoecoae.multiblock.status.idle";
    private int previewStatusArg1;
    private int previewStatusArg2;
    private boolean buildInProgress;
    private transient MultiBlockBuildSession buildSession;
    private transient UUID buildPlayerId;

    public ECOStorageSystemBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, IECOTier tier) {
        super(type, pos, blockState);
        this.tier = tier;
        resetStorageInfos();

        getMainNode().addService(IGridTickable.class, this);
        getMainNode().addService(IStorageProvider.class, this);
    }

    public static ECOStorageSystemBlockEntity createL4(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L4);
    }

    public static ECOStorageSystemBlockEntity createL6(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L6);
    }

    public static ECOStorageSystemBlockEntity createL9(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L9);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(256 + (1 << (1 + 4 * tier.getTier())));
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        updateHostStorageState();
        if (updateExposed) {
            markStorageStatsDirty();
            updateInfos();
        }
    }

    public void onStorageClusterFormed() {
        if (level == null || level.isClientSide || !formed || cluster == null) {
            return;
        }
        updateHostStorageState();
        markStorageStatsDirty();
        updateInfos();
        requestProviderUpdates();
        setChanged();
        markForUpdate();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(20, 20, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        updateHostStorageState();
        long exported = exportStorageInterfaceContents();
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        if (storageInterface != null) {
            storageInterface.recordStorageInterfaceExport(exported);
        }
        updateInfos();
        return TickRateModulation.URGENT;
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if (isStorageInterfaceOutputMode()) {
            return;
        }
        if (canUseHostDomainStorage()) {
            storageMounts.mount(getHostDomainStorage(), priority);
        }
    }

    private void resetStorageInfos() {
        int typeCount = getCellTypeCount();
        usedTypes = new long[typeCount];
        totalTypes = new long[typeCount];
        usedBytes = new long[typeCount];
        totalBytes = new long[typeCount];
        storedEnergy = 0;
        maxEnergy = 0;
        _synUsedTypes = 0;
        _synTotalTypes = 0;
        _synUsedBytes = 0;
        _synTotalBytes = 0;
    }

    /**
     * Core stats recalculation from cluster drives and energy cells.
     * Updates _syn* scalars and per-type arrays but does NOT mark dirty
     * or sync to client. Safe to call on server only.
     */
    @SuppressWarnings("UnstableApiUsage")
    private void recalculateStorageStats() {
        if (cluster != null) {
            storedEnergy = 0;
            maxEnergy = 0;
            for (ECOEnergyCellBlockEntity energyCell : cluster.getEnergyCells()) {
                storedEnergy += (long) energyCell.getAECurrentPower();
                maxEnergy += (long) energyCell.getAEMaxPower();
            }

            int typeCount = getCellTypeCount();
            usedTypes = new long[typeCount];
            totalTypes = new long[typeCount];
            usedBytes = new long[typeCount];
            totalBytes = new long[typeCount];

            if (canUseHostDomainStorage() && hostDomainId != null && level instanceof ServerLevel serverLevel) {
                ECOStorageSavedData.StoredContents domain =
                        ECOStorageSavedData.get(serverLevel).getDomainOrEmpty(hostDomainId);
                _synUsedTypes = domain.storedTypes();
                _synTotalTypes = Long.MAX_VALUE;
                _synUsedBytes = getDomainUsedBytes(domain);
                _synTotalBytes = Long.MAX_VALUE;
                return;
            }

            // Aggregate scalars - always populated regardless of registry-id lookup
            long aggUsedTypes = 0, aggTotalTypes = 0, aggUsedBytes = 0, aggTotalBytes = 0;

            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                IECOStorageCell inv = drive.getCellInventory();
                if (inv == null) continue;

                long st = inv.getStoredItemTypes();
                long tt = inv.getTotalItemTypes();
                long ub = inv.getUsedBytes();
                long tb = inv.getTotalBytes();

                aggUsedTypes = saturatedAdd(aggUsedTypes, st);
                aggTotalTypes = saturatedAdd(aggTotalTypes, tt);
                aggUsedBytes = saturatedAdd(aggUsedBytes, ub);
                aggTotalBytes = saturatedAdd(aggTotalBytes, tb);

                // Per-cell-type arrays - best-effort, may skip if id lookup fails
                ECOCellType cellType = inv.getCellType();
                var reg = NERegistries.cellTypeRegistry();
                int id = reg != null ? reg.getId(cellType) : -1;
                if (id >= 0 && id < typeCount) {
                    usedTypes[id] = saturatedAdd(usedTypes[id], st);
                    totalTypes[id] = saturatedAdd(totalTypes[id], tt);
                    usedBytes[id] = saturatedAdd(usedBytes[id], ub);
                    totalBytes[id] = saturatedAdd(totalBytes[id], tb);
                }
            }

            _synUsedTypes = aggUsedTypes;
            _synTotalTypes = aggTotalTypes;
            _synUsedBytes = aggUsedBytes;
            _synTotalBytes = aggTotalBytes;
        } else {
            resetStorageInfos();
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private void updateInfos() {
        boolean changed = ensureStorageStatsCurrent();
        changed |= applyInfiniteStorageMatrixLocks();
        if (changed) {
            setChanged();
        }
    }

    private boolean ensureStorageStatsCurrent() {
        if (!storageStatsDirty) {
            return false;
        }
        recalculateStorageStats();
        storageStatsDirty = false;
        return true;
    }

    /**
     * Creates a snapshot of current storage stats for S2C UI sync.
     * <p>
     * Stats are grouped by ECOCellType registry key so the screen can display
     * separate rows for Items, Fluids, and future cell types.
     * </p>
     */
    public NEStorageUiState createStorageUiState() {
        if (level != null && !level.isClientSide) {
            ensureStorageStatsCurrent();
        }

        List<NEStorageUiTypeState> typeStates;
        List<NEStorageUiMatrixState> matrixStates;
        if (cluster != null
                && canUseHostDomainStorage()
                && hostDomainId != null
                && level instanceof ServerLevel serverLevel) {
            typeStates = createDomainTypeStates(serverLevel);
            matrixStates = createDomainMatrixStates();
        } else if (cluster != null) {
            // Group by cell type key; LinkedHashMap preserves insertion order
            Map<ResourceLocation, NEStorageUiTypeState> grouped = new LinkedHashMap<>();
            matrixStates = new ArrayList<>(cluster.getDrives().size());
            IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
            Direction top = strategy.getSide(getBlockState(), RelativeSide.TOP);
            Direction left = strategy.getSide(getBlockState(), RelativeSide.RIGHT);
            Direction right = cluster.isMirrored() ? left : left.getOpposite();

            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                BlockPos offset = drive.getBlockPos().subtract(worldPosition);
                int row = 1 - directionDistance(offset, top);
                int column = directionDistance(offset, right) - 1;
                ItemStack cellStack = drive.getCellStack();
                IECOStorageCell inv = drive.getCellInventory();
                if (inv == null || cellStack == null || cellStack.isEmpty()) {
                    matrixStates.add(new NEStorageUiMatrixState(row, column, ItemStack.EMPTY, 0, 0L, 0L, 0L, 0L));
                    continue;
                }

                ECOCellType cellType = inv.getCellType();
                ResourceLocation typeId = getCellTypeKey(cellType);
                String displayName = cellType.desc().getString();

                long st = inv.getStoredItemTypes();
                long tt = inv.getTotalItemTypes();
                long ub = inv.getUsedBytes();
                long tb = inv.getTotalBytes();
                int matrixTier = Math.max(0, Math.min(3, inv.getTier().getTier()));
                matrixStates.add(new NEStorageUiMatrixState(
                        row, column, new ItemStack(cellStack.getItem()), matrixTier, st, tt, ub, tb));

                NEStorageUiTypeState existing = grouped.get(typeId);
                if (existing != null) {
                    grouped.put(
                            typeId,
                            new NEStorageUiTypeState(
                                    typeId,
                                    displayName,
                                    saturatedAdd(existing.usedTypes(), st),
                                    saturatedAdd(existing.totalTypes(), tt),
                                    saturatedAdd(existing.usedBytes(), ub),
                                    saturatedAdd(existing.totalBytes(), tb)));
                } else {
                    grouped.put(typeId, new NEStorageUiTypeState(typeId, displayName, st, tt, ub, tb));
                }
            }
            typeStates = new ArrayList<>(grouped.values());
            // Stable ordering: Items first, Fluids second, others by typeId string
            typeStates.sort(
                    java.util.Comparator.comparingInt((NEStorageUiTypeState s) -> storageTypeSortPriority(s.typeId()))
                            .thenComparing(s -> s.typeId().toString()));
            matrixStates.sort(Comparator.comparingInt(NEStorageUiMatrixState::row)
                    .thenComparingInt(NEStorageUiMatrixState::column));
        } else {
            typeStates = new ArrayList<>();
            matrixStates = List.of();
        }

        return new NEStorageUiState(
                worldPosition,
                typeStates,
                matrixStates,
                storedEnergy,
                maxEnergy,
                formed,
                getInfiniteStorageComponentCount() > 0,
                getInfiniteStorageComponentCount(),
                REQUIRED_INFINITE_STORAGE_COMPONENTS,
                isFullL9MatrixStorage(),
                isInfiniteStorageUnlocked(),
                getL9MatrixDriveCount(),
                getRequiredL9MatrixDriveCount(),
                getL9MatrixStorageCapacityBytes(),
                INFINITE_STORAGE_CAPACITY_THRESHOLD + 1L);
    }

    private static int directionDistance(BlockPos offset, Direction direction) {
        return offset.getX() * direction.getStepX()
                + offset.getY() * direction.getStepY()
                + offset.getZ() * direction.getStepZ();
    }

    private List<NEStorageUiTypeState> createDomainTypeStates(ServerLevel serverLevel) {
        Map<ResourceLocation, NEStorageUiTypeState> grouped = new LinkedHashMap<>();
        Map<Byte, DomainTypeInfo> cellTypeByKeyType = seedDomainTypeStates(grouped);
        if (hostDomainId == null) {
            return new ArrayList<>(grouped.values());
        }
        ECOStorageSavedData.StoredContents domain =
                ECOStorageSavedData.get(serverLevel).getDomainOrEmpty(hostDomainId);
        for (Map.Entry<AEKey, BigInteger> entry : domain.amounts().entrySet()) {
            AEKey key = entry.getKey();
            AEKeyType keyType = key.getType();
            DomainTypeInfo typeInfo =
                    cellTypeByKeyType.getOrDefault(keyType.getRawId(), fallbackDomainTypeInfo(keyType));
            ResourceLocation typeId = typeInfo.typeId();
            String displayName = key.getType().getDescription().getString();
            long bytes = domainEntryUsedBytes(keyType, entry.getValue(), typeInfo.bytesPerType());
            NEStorageUiTypeState existing = grouped.get(typeId);
            long usedTypes = existing == null ? 1L : saturatedAdd(existing.usedTypes(), 1L);
            long usedBytes = existing == null ? bytes : saturatedAdd(existing.usedBytes(), bytes);
            if (existing != null) {
                grouped.put(
                        typeId,
                        new NEStorageUiTypeState(
                                typeId,
                                displayName,
                                usedTypes,
                                Long.MAX_VALUE,
                                usedBytes,
                                Long.MAX_VALUE,
                                Long.toString(Math.max(0L, usedTypes)),
                                infiniteDisplay(),
                                NELDLibText.storageBytes(usedBytes),
                                infiniteDisplay(),
                                true,
                                true));
            } else {
                grouped.put(
                        typeId,
                        new NEStorageUiTypeState(
                                typeId,
                                displayName,
                                usedTypes,
                                Long.MAX_VALUE,
                                usedBytes,
                                Long.MAX_VALUE,
                                Long.toString(Math.max(0L, usedTypes)),
                                infiniteDisplay(),
                                NELDLibText.storageBytes(usedBytes),
                                infiniteDisplay(),
                                true,
                                true));
            }
        }
        List<NEStorageUiTypeState> states = new ArrayList<>(grouped.values());
        states.sort(Comparator.comparingInt((NEStorageUiTypeState s) -> storageTypeSortPriority(s.typeId()))
                .thenComparing(s -> s.typeId().toString()));
        return states;
    }

    private Map<Byte, DomainTypeInfo> seedDomainTypeStates(Map<ResourceLocation, NEStorageUiTypeState> grouped) {
        Map<Byte, DomainTypeInfo> cellTypeByKeyType = new HashMap<>();
        if (cluster != null) {
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                ItemStack stack = drive.getCellStack();
                if (stack != null && stack.getItem() instanceof ECOStorageCellItem cellItem) {
                    ECOCellType cellType = cellItem.getCellType();
                    ResourceLocation typeId = getCellTypeKey(cellType);
                    cellTypeByKeyType.put(
                            cellItem.getKeyType().getRawId(), new DomainTypeInfo(typeId, cellItem.getBytesPerType()));
                    seedDomainTypeState(grouped, typeId, cellType.desc().getString());
                }
            }
        }
        for (AEKeyType keyType : AEKeyTypes.getAll()) {
            if (domainKeyTypes.contains(keyType.getRawId())) {
                DomainTypeInfo typeInfo =
                        cellTypeByKeyType.getOrDefault(keyType.getRawId(), fallbackDomainTypeInfo(keyType));
                cellTypeByKeyType.putIfAbsent(keyType.getRawId(), typeInfo);
                seedDomainTypeState(
                        grouped, typeInfo.typeId(), keyType.getDescription().getString());
            }
        }
        return cellTypeByKeyType;
    }

    private static void seedDomainTypeState(
            Map<ResourceLocation, NEStorageUiTypeState> grouped, ResourceLocation typeId, String displayName) {
        grouped.putIfAbsent(
                typeId,
                new NEStorageUiTypeState(
                        typeId,
                        displayName,
                        0L,
                        Long.MAX_VALUE,
                        0L,
                        Long.MAX_VALUE,
                        "0",
                        infiniteDisplay(),
                        "0",
                        infiniteDisplay(),
                        true,
                        true));
    }

    private static String infiniteDisplay() {
        return "\u221e";
    }

    private static ResourceLocation fallbackDomainTypeId(AEKeyType keyType) {
        if (keyType == AEKeyType.items()) {
            return NeoECOAE.id("items");
        }
        if (keyType == AEKeyType.fluids()) {
            return NeoECOAE.id("fluids");
        }
        ResourceLocation keyTypeId = keyType.getId();
        String path = keyTypeId.getPath();
        if (path.contains("chemical") || path.contains("chem") || path.contains("gas") || path.contains("mekanism")) {
            return NeoECOAE.id("chemicals");
        }
        return keyTypeId;
    }

    private static DomainTypeInfo fallbackDomainTypeInfo(AEKeyType keyType) {
        return new DomainTypeInfo(fallbackDomainTypeId(keyType), 0);
    }

    private long getDomainUsedBytes(ECOStorageSavedData.StoredContents domain) {
        Map<Byte, DomainTypeInfo> cellTypeByKeyType = seedDomainTypeStates(new LinkedHashMap<>());
        BigInteger total = BigInteger.ZERO;
        for (Map.Entry<AEKey, BigInteger> entry : domain.amounts().entrySet()) {
            AEKeyType keyType = entry.getKey().getType();
            DomainTypeInfo typeInfo =
                    cellTypeByKeyType.getOrDefault(keyType.getRawId(), fallbackDomainTypeInfo(keyType));
            total = total.add(domainEntryUsedBytesExact(keyType, entry.getValue(), typeInfo.bytesPerType()));
        }
        return ECOStorageSavedData.saturate(total);
    }

    private static long domainEntryUsedBytes(AEKeyType keyType, BigInteger amount, int bytesPerType) {
        return ECOStorageSavedData.saturate(domainEntryUsedBytesExact(keyType, amount, bytesPerType));
    }

    private static BigInteger domainEntryUsedBytesExact(AEKeyType keyType, BigInteger amount, int bytesPerType) {
        if (amount == null || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger amountPerByte = BigInteger.valueOf(Math.max(1, keyType.getAmountPerByte()));
        BigInteger[] divRem = amount.divideAndRemainder(amountPerByte);
        BigInteger itemBytes = divRem[1].signum() == 0 ? divRem[0] : divRem[0].add(BigInteger.ONE);
        return itemBytes.add(BigInteger.valueOf(Math.max(0, bytesPerType)));
    }

    private List<NEStorageUiMatrixState> createDomainMatrixStates() {
        if (cluster == null) {
            return List.of();
        }
        List<NEStorageUiMatrixState> matrixStates =
                new ArrayList<>(cluster.getDrives().size());
        IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
        Direction top = strategy.getSide(getBlockState(), RelativeSide.TOP);
        Direction left = strategy.getSide(getBlockState(), RelativeSide.RIGHT);
        Direction right = cluster.isMirrored() ? left : left.getOpposite();

        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            BlockPos offset = drive.getBlockPos().subtract(worldPosition);
            int row = 1 - directionDistance(offset, top);
            int column = directionDistance(offset, right) - 1;
            ItemStack cellStack = drive.getCellStack();
            int matrixTier = getStorageCellTier(cellStack);
            if (cellStack == null || cellStack.isEmpty() || matrixTier < 0) {
                matrixStates.add(new NEStorageUiMatrixState(row, column, ItemStack.EMPTY, 0, 0L, 0L, 0L, 0L));
            } else {
                matrixStates.add(new NEStorageUiMatrixState(
                        row,
                        column,
                        new ItemStack(cellStack.getItem()),
                        Math.max(0, Math.min(3, matrixTier)),
                        0L,
                        Long.MAX_VALUE,
                        0L,
                        Long.MAX_VALUE,
                        true));
            }
        }
        matrixStates.sort(
                Comparator.comparingInt(NEStorageUiMatrixState::row).thenComparingInt(NEStorageUiMatrixState::column));
        return matrixStates;
    }

    @Override
    public ModularUI createUI(Player player) {
        return NELDLibUis.createStorageController(this, player);
    }

    /**
     * Returns the stable identity key for a cell type.
     * Uses the {@code id} field embedded in {@link ECOCellType} directly,
     * avoiding {@code Registry.getKey()} which is unreliable for custom
     * Registrate-built registries.
     */
    private static ResourceLocation getCellTypeKey(ECOCellType cellType) {
        ResourceLocation id = cellType.id();
        return id != null ? id : ResourceLocation.fromNamespaceAndPath(NeoECOAE.MOD_ID, "unknown");
    }

    /**
     * Returns a sort priority for stable UI ordering.
     * Items (0) always first, Fluids (1) second, other types (100+) sorted
     * by their full typeId string.
     */
    private static int storageTypeSortPriority(ResourceLocation id) {
        String path = id.getPath();
        if (id.equals(NeoECOAE.id("items")) || path.equals("item") || path.equals("items")) {
            return 0;
        }
        if (id.equals(NeoECOAE.id("fluids")) || path.equals("fluid") || path.equals("fluids")) {
            return 1;
        }
        return 100;
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !buildInProgress || buildSession == null) {
            return;
        }

        ServerPlayer buildPlayer = buildPlayerId == null
                ? null
                : serverLevel.getServer().getPlayerList().getPlayer(buildPlayerId);
        if (buildPlayer == null) {
            int remainingBlocks = buildSession.getRemainingBlockCount();
            buildSession = null;
            buildPlayerId = null;
            buildInProgress = false;
            syncPreview(
                    remainingBlocks,
                    0,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.builder_unavailable");
            return;
        }

        switch (MultiBlockPlacementService.tickBuild(serverLevel, buildSession, buildPlayer)) {
            case WAITING -> {}
            case ADVANCED -> syncPreview(
                    buildSession.getRemainingBlockCount(),
                    0,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.building",
                    buildSession.getPlacedBlockCount(),
                    buildSession.getTotalBlocks());
            case COMPLETED -> {
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                rebuildMultiblock();
                syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            }
            case BLOCKED -> {
                int remainingBlocks = buildSession.getRemainingBlockCount();
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                syncPreview(
                        remainingBlocks,
                        1,
                        previewReusedBlocks,
                        previewRequiredItems,
                        "gui.neoecoae.multiblock.status.build_interrupted");
            }
        }
    }

    public long getStoredEnergy() {
        return storedEnergy;
    }

    public boolean isFormed() {
        return formed;
    }

    public long getMaxEnergy() {
        return maxEnergy;
    }

    // Scalar synced fields - written directly to avoid long[] array sync issues on client
    private long _synUsedTypes;
    private long _synTotalTypes;
    private long _synUsedBytes;
    private long _synTotalBytes;

    public long getTotalUsedBytes() {
        return _synUsedBytes;
    }

    public long getTotalBytes() {
        return _synTotalBytes;
    }

    public long getTotalUsedTypes() {
        return _synUsedTypes;
    }

    public long getTotalTypes() {
        return _synTotalTypes;
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

    public boolean canUseInfiniteStorageComponents() {
        return tier.getTier() == ECOTier.L9.getTier() && isInfiniteStorageComponentAvailable();
    }

    public static boolean isInfiniteStorageComponentAvailable() {
        return BuiltInRegistries.ITEM.get(INFINITE_STORAGE_COMPONENT_ID) != Items.AIR;
    }

    public boolean isInfiniteStorageUnlocked() {
        return hostMode == ECOStorageHostMode.FORMED_INFINITE && hostDomainId != null;
    }

    private boolean isInfiniteUnlockConfigured() {
        return tier.getTier() >= ECOTier.L9.getTier()
                && getInfiniteStorageComponentCount() >= REQUIRED_INFINITE_STORAGE_COMPONENTS
                && isFullL9MatrixStorage()
                && getL9MatrixStorageCapacityBytes() > INFINITE_STORAGE_CAPACITY_THRESHOLD;
    }

    public boolean canAcceptInfiniteStorageMatrices() {
        return tier.getTier() >= ECOTier.L9.getTier()
                && getInfiniteStorageComponentCount() >= REQUIRED_INFINITE_STORAGE_COMPONENTS
                && (hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE
                        || hostMode == ECOStorageHostMode.FORMED_INFINITE
                        || isInfiniteUnlockConfigured());
    }

    public boolean isFullL9MatrixStorage() {
        if (cluster == null || cluster.getDrives().isEmpty()) {
            return false;
        }
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (getStorageCellTier(stack) < ECOTier.L9.getTier()) {
                return false;
            }
        }
        return true;
    }

    public int getL9MatrixDriveCount() {
        if (cluster == null) {
            return 0;
        }
        int count = 0;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            if (getStorageCellTier(drive.getCellStack()) >= ECOTier.L9.getTier()) {
                count++;
            }
        }
        return count;
    }

    public int getRequiredL9MatrixDriveCount() {
        return cluster == null
                ? Math.max(0, selectedBuildLength) * 3
                : cluster.getDrives().size();
    }

    public long getL9MatrixStorageCapacityBytes() {
        if (cluster == null) {
            return 0L;
        }
        long capacity = 0L;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (stack != null
                    && stack.getItem() instanceof ECOStorageCellItem cell
                    && cell.getTier().getTier() >= ECOTier.L9.getTier()) {
                capacity = saturatedAdd(
                        capacity,
                        NEConfig.getEcoStorageCellCapacity(
                                cell.getTier(), cell.getTier().getStorageTotalBytes()));
            }
        }
        return capacity;
    }

    private boolean applyInfiniteStorageMatrixLocks() {
        if (level == null || level.isClientSide || cluster == null) {
            return false;
        }
        if (tryExitInfiniteModeWithoutComponent()) {
            return true;
        }
        if (hostMode == ECOStorageHostMode.FORMED_NORMAL && isInfiniteUnlockConfigured()) {
            return startInfiniteMigration();
        }
        if (hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            return resumeInfiniteMigration();
        }
        return false;
    }

    private void onInfiniteStorageComponentChanged() {
        setChanged();
        markStorageStatsDirty();
        applyInfiniteStorageMatrixLocks();
        if (cluster != null) {
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                drive.requestStorageProviderUpdate();
                drive.scheduleRenderUpdate();
            }
        }
        markForUpdate();
    }

    private static boolean isInfiniteStorageComponent(ItemStack stack) {
        Item component = BuiltInRegistries.ITEM.get(INFINITE_STORAGE_COMPONENT_ID);
        return component != Items.AIR && stack.is(component);
    }

    private void updateHostStorageState() {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        if (!formed) {
            if (hostMode == ECOStorageHostMode.UNFORMED || hostMode == ECOStorageHostMode.FORMED_NORMAL) {
                hostMode = ECOStorageHostMode.UNFORMED;
            }
            return;
        }
        if (hostMode == ECOStorageHostMode.UNFORMED) {
            hostMode = ECOStorageHostMode.FORMED_NORMAL;
            setChanged();
        }
        if (hostMode == ECOStorageHostMode.FORMED_NORMAL
                && !isInfiniteUnlockConfigured()
                && areAllDriveCellsPortableEmpty()) {
            resetStorageInterfaceToStorageMode();
        }
        if (tryExitInfiniteModeWithoutComponent()) {
            return;
        }
        if (hostMode == ECOStorageHostMode.FORMED_NORMAL && isInfiniteUnlockConfigured()) {
            startInfiniteMigration();
        } else if (hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            resumeInfiniteMigration();
        }
    }

    private boolean startInfiniteMigration() {
        if (!(level instanceof ServerLevel serverLevel)
                || cluster == null
                || !formed
                || !isInfiniteUnlockConfigured()) {
            return false;
        }

        UUID domainId = hostDomainId != null ? hostDomainId : UUID.randomUUID();
        List<UUID> diskIds = new ArrayList<>();
        Set<UUID> seenDisks = new HashSet<>();
        Set<Byte> keyTypes = new HashSet<>();
        ECOStorageSavedData data = ECOStorageSavedData.get(serverLevel);

        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (!isMigrationCandidate(stack, domainId)) {
                LOGGER.warn(
                        "Cannot start ECO infinite storage migration: invalid matrix in drive {}", drive.getBlockPos());
                return false;
            }
            UUID diskId = ECOStorageCellMetadata.getOrCreateDiskId(stack);
            if (!seenDisks.add(diskId)) {
                LOGGER.warn("Cannot start ECO infinite storage migration: duplicate diskId {}", diskId);
                return false;
            }
            data.importLegacyDiskContents(diskId, ECOStorageCellMetadata.readLegacyStacks(stack));
            ECOStorageCellMetadata.clearLegacyStacks(stack);
            diskIds.add(diskId);
            if (stack.getItem() instanceof ECOStorageCellItem cellItem) {
                keyTypes.add(cellItem.getKeyType().getRawId());
            }
        }

        if (diskIds.isEmpty()) {
            return false;
        }

        hostDomainId = domainId;
        hostMode = ECOStorageHostMode.MIGRATING_TO_INFINITE;
        memberDiskIds.clear();
        memberDiskIds.addAll(diskIds);
        migrationSteps.clear();
        for (UUID diskId : diskIds) {
            migrationSteps.put(diskId, MIGRATION_NOT_STARTED);
        }
        domainKeyTypes.clear();
        domainKeyTypes.addAll(keyTypes);
        data.beginMigration(domainId, List.copyOf(diskIds));

        for (int i = 0; i < cluster.getDrives().size(); i++) {
            ECODriveBlockEntity drive = cluster.getDrives().get(i);
            ItemStack stack = drive.getCellStack();
            ECOStorageCellMetadata.markMigrating(stack, domainId, i);
            drive.onCellMetadataChanged();
        }

        setChanged();
        markForUpdate();
        requestProviderUpdates();
        resumeInfiniteMigration();
        return true;
    }

    private boolean resumeInfiniteMigration() {
        if (!(level instanceof ServerLevel serverLevel) || cluster == null || hostDomainId == null) {
            return false;
        }
        ECOStorageSavedData data = ECOStorageSavedData.get(serverLevel);
        data.beginMigration(hostDomainId, List.copyOf(memberDiskIds));

        boolean changed = false;
        for (int i = 0; i < memberDiskIds.size(); i++) {
            UUID diskId = memberDiskIds.get(i);
            ECODriveBlockEntity drive = findDriveByDiskId(diskId);
            if (drive == null) {
                LOGGER.warn("ECO infinite storage migration paused: missing member disk {}", diskId);
                return changed;
            }
            ItemStack stack = drive.getCellStack();
            if (stack == null || stack.isEmpty()) {
                LOGGER.warn("ECO infinite storage migration paused: empty member slot for disk {}", diskId);
                return changed;
            }

            int step = migrationSteps.getOrDefault(diskId, MIGRATION_NOT_STARTED);
            if (step < MIGRATION_COPYING) {
                ECOStorageCellMetadata.markMigrating(stack, hostDomainId, i);
                setMigrationStep(diskId, MIGRATION_COPYING);
                drive.onCellMetadataChanged();
                changed = true;
                step = MIGRATION_COPYING;
            }
            if (step == MIGRATION_COPYING) {
                data.commitDiskToDomain(hostDomainId, diskId);
                setMigrationStep(diskId, MIGRATION_COMMITTED_TO_DOMAIN);
                changed = true;
                step = MIGRATION_COMMITTED_TO_DOMAIN;
            }
            if (step == MIGRATION_COMMITTED_TO_DOMAIN) {
                data.clearDisk(diskId);
                setMigrationStep(diskId, MIGRATION_SOURCE_CLEARED);
                changed = true;
                step = MIGRATION_SOURCE_CLEARED;
            }
            if (step == MIGRATION_SOURCE_CLEARED) {
                ECOStorageCellMetadata.markDomainMember(stack, hostDomainId, i);
                setMigrationStep(diskId, MIGRATION_BOUND_AS_MEMBER);
                drive.onCellMetadataChanged();
                changed = true;
            }
        }

        boolean complete = !memberDiskIds.isEmpty();
        for (UUID diskId : memberDiskIds) {
            if (migrationSteps.getOrDefault(diskId, MIGRATION_NOT_STARTED) != MIGRATION_BOUND_AS_MEMBER) {
                complete = false;
                break;
            }
        }
        if (complete) {
            hostMode = ECOStorageHostMode.FORMED_INFINITE;
            migrationSteps.clear();
            data.finishMigration(hostDomainId);
            markStorageStatsDirty();
            requestProviderUpdates();
            markForUpdate();
            setChanged();
            changed = true;
        }
        return changed;
    }

    private void setMigrationStep(UUID diskId, int step) {
        migrationSteps.put(diskId, step);
        setChanged();
    }

    private boolean isMigrationCandidate(@Nullable ItemStack stack, UUID domainId) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ECOStorageCellItem)) {
            return false;
        }
        if (ECOStorageCellMetadata.isDomainMember(stack) || ECOStorageCellMetadata.isMigrating(stack)) {
            UUID stackDomain = ECOStorageCellMetadata.getHostDomainId(stack);
            return domainId.equals(stackDomain);
        }
        return true;
    }

    @Nullable private ECODriveBlockEntity findDriveByDiskId(UUID diskId) {
        if (cluster == null) {
            return null;
        }
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (stack != null && diskId.equals(ECOStorageCellMetadata.getDiskId(stack))) {
                return drive;
            }
        }
        return null;
    }

    public boolean canMountDriveCell(ECODriveBlockEntity drive) {
        if (isStorageInterfaceOutputMode()) {
            return false;
        }
        if (!formed || hostMode != ECOStorageHostMode.FORMED_NORMAL || cluster == null) {
            return false;
        }
        ItemStack stack = drive.getCellStack();
        if (stack == null
                || stack.isEmpty()
                || ECOStorageCellMetadata.hasNonPortableState(stack)
                || ECOStorageCellMetadata.isLegacyInfiniteLocked(stack)) {
            return false;
        }
        UUID diskId = ECOStorageCellMetadata.getDiskId(stack);
        if (diskId == null) {
            return true;
        }
        for (ECODriveBlockEntity candidate : cluster.getDrives()) {
            ItemStack candidateStack = candidate.getCellStack();
            if (candidateStack != null && diskId.equals(ECOStorageCellMetadata.getDiskId(candidateStack))) {
                return candidate == drive;
            }
        }
        return true;
    }

    public boolean isStorageInterfaceOutputMode() {
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        return storageInterface != null && storageInterface.isStorageOutputMode();
    }

    private void resetStorageInterfaceToStorageMode() {
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        if (storageInterface != null && storageInterface.isStorageOutputMode()) {
            storageInterface.setStorageInterfaceMode(ECOStorageInterfaceMode.STORAGE);
        }
    }

    private boolean areAllDriveCellsPortableEmpty() {
        if (cluster == null || cluster.getDrives().isEmpty()) {
            return false;
        }
        boolean foundCell = false;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (ECOStorageCellMetadata.hasNonPortableState(stack)
                    || ECOStorageCellMetadata.isLegacyInfiniteLocked(stack)) {
                return false;
            }
            IECOStorageCell cell = drive.getCellInventory();
            if (cell == null) {
                return false;
            }
            foundCell = true;
            if (cell.getStoredItemTypes() > 0 || cell.getUsedBytes() > 0) {
                return false;
            }
        }
        return foundCell;
    }

    public void onStorageInterfaceModeChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        markStorageStatsDirty();
        requestProviderUpdates();
        markForUpdate();
        setChanged();
    }

    public boolean canAcceptCellStack(ItemStack stack) {
        if (ECOStorageCellMetadata.hasNonPortableState(stack) || ECOStorageCellMetadata.isLegacyInfiniteLocked(stack)) {
            UUID stackDomain = ECOStorageCellMetadata.getHostDomainId(stack);
            if (stackDomain == null && ECOStorageCellMetadata.isLegacyInfiniteLocked(stack)) {
                return tier.getTier() >= ECOTier.L9.getTier()
                        && getInfiniteStorageComponentCount() >= REQUIRED_INFINITE_STORAGE_COMPONENTS;
            }
            return hostDomainId != null
                    && hostDomainId.equals(stackDomain)
                    && (hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE
                            || hostMode == ECOStorageHostMode.FORMED_INFINITE);
        }
        return hostMode == ECOStorageHostMode.UNFORMED || hostMode == ECOStorageHostMode.FORMED_NORMAL;
    }

    public boolean canExtractDriveCell(ECODriveBlockEntity drive) {
        ItemStack stack = drive.getCellStack();
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        if (hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE
                || hostMode == ECOStorageHostMode.FORMED_INFINITE
                || ECOStorageCellMetadata.hasNonPortableState(stack)
                || ECOStorageCellMetadata.isLegacyInfiniteLocked(stack)) {
            return false;
        }
        return true;
    }

    public boolean canUseHostDomainStorage() {
        return formed && hostMode == ECOStorageHostMode.FORMED_INFINITE && hostDomainId != null;
    }

    @Nullable private ECOMachineInterfaceBlockEntity<NEStorageCluster> getStorageInterface() {
        return cluster == null ? null : cluster.getTheInterface();
    }

    private long exportStorageInterfaceContents() {
        if (!isStorageInterfaceOutputMode() || !formed || cluster == null) {
            return 0L;
        }
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        if (storageInterface == null || !storageInterface.getMainNode().isOnline()) {
            return 0L;
        }
        var grid = storageInterface.getMainNode().getGrid();
        if (grid == null) {
            return 0L;
        }
        MEStorage target = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(storageInterface);
        if (canUseHostDomainStorage()) {
            return exportFromStorage(getHostDomainStorage(), target, source, STORAGE_INTERFACE_EXPORT_KEYS_PER_TICK);
        }

        long exported = 0L;
        int remainingKeys = STORAGE_INTERFACE_EXPORT_KEYS_PER_TICK;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            if (remainingKeys <= 0) {
                break;
            }
            IECOStorageCell cell = drive.getCellInventory();
            if (cell == null || !canMountDriveCellForExport(drive)) {
                continue;
            }
            ExportResult result = exportFromStorageLimited(cell, target, source, remainingKeys);
            exported = saturatedAdd(exported, result.exported());
            remainingKeys -= result.keysVisited();
        }
        if (exported > 0L) {
            markStorageStatsDirty();
            setChanged();
            markForUpdate();
        }
        return exported;
    }

    private boolean canMountDriveCellForExport(ECODriveBlockEntity drive) {
        if (!formed || hostMode != ECOStorageHostMode.FORMED_NORMAL || cluster == null) {
            return false;
        }
        ItemStack stack = drive.getCellStack();
        if (stack == null
                || stack.isEmpty()
                || ECOStorageCellMetadata.hasNonPortableState(stack)
                || ECOStorageCellMetadata.isLegacyInfiniteLocked(stack)) {
            return false;
        }
        IECOStorageCell cell = drive.getCellInventory();
        return cell != null && tier.compareTo(cell.getTier()) >= 0;
    }

    private long exportFromStorage(
            MEStorage sourceStorage, MEStorage targetStorage, IActionSource source, int maxKeys) {
        ExportResult result = exportFromStorageLimited(sourceStorage, targetStorage, source, maxKeys);
        long exported = result.exported();
        if (exported > 0L) {
            markStorageStatsDirty();
            setChanged();
            markForUpdate();
        }
        return exported;
    }

    private ExportResult exportFromStorageLimited(
            MEStorage sourceStorage, MEStorage targetStorage, IActionSource source, int maxKeys) {
        if (maxKeys <= 0) {
            return new ExportResult(0L, 0);
        }
        appeng.api.stacks.KeyCounter available = new appeng.api.stacks.KeyCounter();
        sourceStorage.getAvailableStacks(available);
        long exported = 0L;
        int keysVisited = 0;
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (keysVisited >= maxKeys) {
                break;
            }
            long amount = entry.getLongValue();
            if (amount <= 0L) {
                continue;
            }
            keysVisited++;
            long moved = exportKey(sourceStorage, targetStorage, source, entry.getKey(), amount);
            if (moved > 0L) {
                exported = saturatedAdd(exported, moved);
            }
        }
        return new ExportResult(exported, keysVisited);
    }

    private long exportKey(
            MEStorage sourceStorage, MEStorage targetStorage, IActionSource source, AEKey key, long availableAmount) {
        long request = Math.max(0L, availableAmount);
        if (request <= 0L) {
            return 0L;
        }
        long accepted = targetStorage.insert(key, request, Actionable.SIMULATE, source);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = sourceStorage.extract(key, Math.min(request, accepted), Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0L;
        }
        long inserted = targetStorage.insert(key, extracted, Actionable.MODULATE, source);
        if (inserted < extracted) {
            long remainder = extracted - Math.max(0L, inserted);
            sourceStorage.insert(key, remainder, Actionable.MODULATE, source);
        }
        return Math.max(0L, inserted);
    }

    public boolean canDomainStore(AEKey key) {
        return domainKeyTypes.isEmpty() || domainKeyTypes.contains(key.getType().getRawId());
    }

    public @Nullable UUID getHostDomainId() {
        return hostDomainId;
    }

    public @Nullable ServerLevel getStorageServerLevel() {
        return level instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    public void onHostDomainContentChanged() {
        markStorageStatsDirty();
        setChanged();
        markForUpdate();
    }

    private ECOHostDomainStorage getHostDomainStorage() {
        if (hostDomainStorage == null) {
            hostDomainStorage = new ECOHostDomainStorage(this);
        }
        return hostDomainStorage;
    }

    private boolean canExitInfiniteMode() {
        if (!(level instanceof ServerLevel serverLevel)
                || cluster == null
                || hostMode != ECOStorageHostMode.FORMED_INFINITE
                || hostDomainId == null
                || !formed) {
            return false;
        }
        if (!ECOStorageSavedData.get(serverLevel).isDomainEmpty(hostDomainId)) {
            return false;
        }
        for (UUID diskId : memberDiskIds) {
            if (findDriveByDiskId(diskId) == null) {
                return false;
            }
        }
        return true;
    }

    private boolean tryExitInfiniteModeWithoutComponent() {
        if (hostMode != ECOStorageHostMode.FORMED_INFINITE || isInfiniteUnlockConfigured()) {
            return false;
        }
        if (!canExitInfiniteMode()) {
            return false;
        }
        exitInfiniteMode();
        return true;
    }

    private boolean tryExitInfiniteModeForDismantle() {
        if (hostMode != ECOStorageHostMode.FORMED_INFINITE || !canExitInfiniteMode()) {
            return false;
        }
        exitInfiniteMode();
        return true;
    }

    private void exitInfiniteMode() {
        if (!(level instanceof ServerLevel serverLevel) || !canExitInfiniteMode() || hostDomainId == null) {
            return;
        }
        for (int i = 0; i < memberDiskIds.size(); i++) {
            ECODriveBlockEntity drive = findDriveByDiskId(memberDiskIds.get(i));
            if (drive == null) {
                return;
            }
            ItemStack stack = drive.getCellStack();
            if (stack != null && !stack.isEmpty()) {
                ECOStorageCellMetadata.clearDomainBinding(stack);
                drive.onCellMetadataChanged();
            }
        }
        ECOStorageSavedData.get(serverLevel).removeDomain(hostDomainId);
        hostDomainId = null;
        memberDiskIds.clear();
        migrationSteps.clear();
        domainKeyTypes.clear();
        hostMode = ECOStorageHostMode.FORMED_NORMAL;
        resetStorageInterfaceToStorageMode();
        markStorageStatsDirty();
        requestProviderUpdates();
        markForUpdate();
        setChanged();
    }

    private void requestProviderUpdates() {
        IStorageProvider.requestUpdate(getMainNode());
        if (cluster != null) {
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                drive.requestStorageProviderUpdate();
                drive.scheduleRenderUpdate();
            }
        }
    }

    private static int getStorageCellTier(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ECOStorageCellItem cellItem)) {
            return -1;
        }
        return cellItem.getTier().getTier();
    }

    public Component getPreviewStatusComponent() {
        return buildPreviewStatusComponent();
    }

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
        return NEMultiBlocks.getStorageSystemDefinition(tier);
    }

    @Override
    public void setSelectedBuildLength(int length) {
        this.selectedBuildLength = Mth.clamp(length, getMinBuildLength(), getMaxBuildLength());
    }

    @Override
    public int getMinBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMin();
    }

    @Override
    public int getMaxBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMax();
    }

    @Override
    public void previewStructure(ServerPlayer player, int displayLength) {
        previewStructure(player, displayLength, false);
    }

    @Override
    public void previewStructure(ServerPlayer player, int displayLength, boolean mirrored) {
        setSelectedBuildLength(displayLength);
        previewStructure((Player) player, mirrored);
    }

    @Override
    public void autoBuild(ServerPlayer player, int displayLength) {
        autoBuild(player, displayLength, false);
    }

    @Override
    public void autoBuild(ServerPlayer player, int displayLength, boolean mirrored) {
        setSelectedBuildLength(displayLength);
        autoBuild((Player) player, mirrored);
    }

    @Deprecated
    @Override
    public void previewStructure(ServerPlayer player) {
        previewStructure((Player) player);
    }

    @Deprecated
    @Override
    public void autoBuild(ServerPlayer player) {
        autoBuild((Player) player);
    }

    @Override
    public void dismantle(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        player.closeContainer();
        boolean dismantled = MultiBlockPlacementService.dismantle(serverLevel, this, player);
        syncPreview(
                0,
                0,
                0,
                0,
                dismantled
                        ? "gui.neoecoae.multiblock.status.dismantled"
                        : "gui.neoecoae.multiblock.status.dismantle_failed");
    }

    // Legacy public accessors

    public int getSelectedBuildLength() {
        return selectedBuildLength;
    }

    public int getPreviewMissingBlocks() {
        return previewMissingBlocks;
    }

    public int getPreviewConflictBlocks() {
        return previewConflictBlocks;
    }

    public int getPreviewReusedBlocks() {
        return previewReusedBlocks;
    }

    public int getPreviewRequiredItems() {
        return previewRequiredItems;
    }

    public boolean isBuildInProgress() {
        return buildInProgress;
    }

    /**
     * Called by Drive block entities to notify the controller that storage
     * stats should be recalculated (cell inserted, removed, or content changed).
     * Only executes on the server side.
     */
    public void refreshStorageUiState() {
        if (level == null || level.isClientSide) {
            return;
        }
        markStorageStatsDirty();
    }

    /**
     * Marks the cached storage stats (per-type used/total bytes and types)
     * as stale. The next call to {@link #ensureStorageStatsCurrent()} will
     * recalculate from cluster drives and trigger a UI state push.
     */
    public void markStorageStatsDirty() {
        storageStatsDirty = true;
    }

    // 鈹€鈹€ IPriorityHost implementation 鈹€鈹€

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        setChanged();
        // Notify all drives to remount with the new priority
        if (cluster != null) {
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                drive.requestStorageProviderUpdate();
            }
        }
        markForUpdate();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getBlockState().getBlock().asItem());
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (player instanceof ServerPlayer serverPlayer && level != null && !level.isClientSide && !isRemoved()) {
            BlockEntityUIFactory.INSTANCE.openUI(this, serverPlayer);
        }
    }

    private int getCellTypeCount() {
        var reg = NERegistries.cellTypeRegistry();
        return Math.max(reg != null ? reg.size() : 1, 1);
    }

    private static long sum(long[] values) {
        if (values == null) {
            return 0;
        }
        long result = 0;
        for (long value : values) {
            result += value;
        }
        return result;
    }

    // increaseBuildLength / decreaseBuildLength are provided by INEMultiblockBuildHost default

    @Override
    public BuildPreviewState getBuildPreview() {
        return buildPreview;
    }

    public void previewStructure(Player player) {
        previewStructure(player, false);
    }

    public void previewStructure(Player player, boolean mirrored) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (formed) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }
        if (buildInProgress && buildSession != null) {
            syncPreview(
                    buildSession.getRemainingBlockCount(),
                    0,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.building",
                    buildSession.getPlacedBlockCount(),
                    buildSession.getTotalBlocks());
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }
        selectedBuildLength =
                net.minecraft.util.Mth.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
                serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength, mirrored);
        boolean hasMaterials = player instanceof ServerPlayer serverPlayer
                && MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems());
        String statusKey = plan.getConflictPositions().isEmpty()
                ? (plan.getMissingBlocks().isEmpty()
                        ? "gui.neoecoae.multiblock.status.structure_ready"
                        : (hasMaterials
                                ? "gui.neoecoae.multiblock.status.ready_to_build"
                                : "gui.neoecoae.multiblock.status.not_enough_items"))
                : "gui.neoecoae.multiblock.status.conflicts_detected";
        syncPreview(
                plan.getMissingBlocks().size(),
                plan.getConflictPositions().size(),
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                statusKey);
    }

    public void autoBuild(Player player) {
        autoBuild(player, false);
    }

    public void autoBuild(Player player, boolean mirrored) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.closeContainer();
        if (formed) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }
        if (buildInProgress) {
            syncPreview(
                    previewMissingBlocks,
                    previewConflictBlocks,
                    previewReusedBlocks,
                    previewRequiredItems,
                    "gui.neoecoae.multiblock.status.build_already_in_progress");
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }
        selectedBuildLength =
                net.minecraft.util.Mth.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
                serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength, mirrored);
        if (!plan.getConflictPositions().isEmpty()) {
            syncPreview(
                    plan.getMissingBlocks().size(),
                    plan.getConflictPositions().size(),
                    plan.getReusedBlockCount(),
                    plan.getRequiredItemCount(),
                    "gui.neoecoae.multiblock.status.conflicts_detected");
            return;
        }
        if (!serverPlayer.isCreative()
                && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            syncPreview(
                    plan.getMissingBlocks().size(),
                    0,
                    plan.getReusedBlockCount(),
                    plan.getRequiredItemCount(),
                    "gui.neoecoae.multiblock.status.not_enough_items");
            return;
        }
        if (plan.getMissingBlocks().isEmpty()) {
            rebuildMultiblock();
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            return;
        }
        if (serverPlayer.isCreative()) {
            if (!MultiBlockPlacementService.buildInstant(serverLevel, plan)) {
                syncPreview(
                        plan.getMissingBlocks().size(),
                        plan.getConflictPositions().size(),
                        plan.getReusedBlockCount(),
                        plan.getRequiredItemCount(),
                        "gui.neoecoae.multiblock.status.build_failed");
                return;
            }
            rebuildMultiblock();
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            return;
        }
        buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        buildPlayerId = serverPlayer.getUUID();
        buildInProgress = true;
        syncPreview(
                plan.getMissingBlocks().size(),
                0,
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                "gui.neoecoae.multiblock.status.building",
                buildSession.getPlacedBlockCount(),
                buildSession.getTotalBlocks());
    }

    @Override
    public void syncPreview(
            int missingBlocks,
            int conflictBlocks,
            int reusedBlocks,
            int requiredItems,
            String statusKey,
            int statusArg1,
            int statusArg2) {
        previewMissingBlocks = missingBlocks;
        previewConflictBlocks = conflictBlocks;
        previewReusedBlocks = reusedBlocks;
        previewRequiredItems = requiredItems;
        previewStatusKey = statusKey;
        previewStatusArg1 = statusArg1;
        previewStatusArg2 = statusArg2;
        setChanged();
        markForUpdate();
    }

    @Override
    public void syncPreview(
            int missingBlocks, int conflictBlocks, int reusedBlocks, int requiredItems, String statusKey) {
        syncPreview(missingBlocks, conflictBlocks, reusedBlocks, requiredItems, statusKey, 0, 0);
    }

    @Override
    public void markPreviewDirty() {
        setChanged();
        markForUpdate();
    }

    @Override
    public void resetPreview(String statusKey) {
        syncPreview(0, 0, 0, 0, statusKey);
    }

    // buildPreviewStatusComponent() is provided by INEMultiblockBuildHost default

    // NBT persistence
    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("selectedBuildLength", selectedBuildLength);
        tag.putInt("priority", priority);
        tag.put(NBT_INFINITE_STORAGE_COMPONENT, infiniteStorageComponent.serializeNBT());
        tag.putString(NBT_HOST_MODE, hostMode.name());
        if (hostDomainId != null) {
            tag.putString(NBT_HOST_DOMAIN_ID, hostDomainId.toString());
        }
        tag.put(NBT_MEMBER_DISKS, writeUuidList(memberDiskIds));
        tag.put(NBT_MIGRATION_STEPS, writeMigrationSteps());
        tag.putByteArray(NBT_DOMAIN_KEY_TYPES, writeByteSet(domainKeyTypes));
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        selectedBuildLength = tag.getInt("selectedBuildLength");
        if (selectedBuildLength < 1) selectedBuildLength = 1;
        priority = tag.getInt("priority");
        if (tag.contains(NBT_INFINITE_STORAGE_COMPONENT)) {
            infiniteStorageComponent.deserializeNBT(tag.getCompound(NBT_INFINITE_STORAGE_COMPONENT));
        }
        hostMode = ECOStorageHostMode.byName(tag.getString(NBT_HOST_MODE));
        hostDomainId =
                tag.contains(NBT_HOST_DOMAIN_ID, Tag.TAG_STRING) ? readUuid(tag.getString(NBT_HOST_DOMAIN_ID)) : null;
        memberDiskIds.clear();
        memberDiskIds.addAll(readUuidList(tag.getList(NBT_MEMBER_DISKS, Tag.TAG_STRING)));
        migrationSteps.clear();
        readMigrationSteps(tag.getList(NBT_MIGRATION_STEPS, Tag.TAG_COMPOUND));
        domainKeyTypes.clear();
        for (byte rawId : tag.getByteArray(NBT_DOMAIN_KEY_TYPES)) {
            domainKeyTypes.add(rawId);
        }
        // Safety: build session is transient; reset in-progress state on load
        buildInProgress = false;
        previewMissingBlocks = 0;
        previewConflictBlocks = 0;
        previewReusedBlocks = 0;
        previewRequiredItems = 0;
        previewStatusKey = "gui.neoecoae.multiblock.status.idle";
        previewStatusArg1 = 0;
        previewStatusArg2 = 0;
    }

    @Override
    public void exportSettings(SettingsFrom mode, CompoundTag output, @Nullable Player player) {
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            tryExitInfiniteModeForDismantle();
        }
        super.exportSettings(mode, output, player);
    }

    // UI sync (Layer 1: chunk-load NBT)
    // getUpdateTag/handleUpdateTag/getUpdatePacket are provided by NEBlockEntity.
    // We only need to override writeUiSyncTag/readUiSyncTag.

    @Override
    protected void writeUiSyncTag(CompoundTag tag) {
        tag.putLong("neo_storedEnergy", storedEnergy);
        tag.putLong("neo_maxEnergy", maxEnergy);
        tag.putBoolean("neo_formed", formed);
        tag.putLong("neo_usedTypes_s", _synUsedTypes);
        tag.putLong("neo_totalTypes_s", _synTotalTypes);
        tag.putLong("neo_usedBytes_s", _synUsedBytes);
        tag.putLong("neo_totalBytes_s", _synTotalBytes);
        if (usedTypes != null) tag.putLongArray("neo_usedTypes", usedTypes);
        if (totalTypes != null) tag.putLongArray("neo_totalTypes", totalTypes);
        if (usedBytes != null) tag.putLongArray("neo_usedBytes", usedBytes);
        if (totalBytes != null) tag.putLongArray("neo_totalBytes", totalBytes);
        // Build/preview state is delegated to BuildPreviewState
        // Note: individual fields (selectedBuildLength, preview*, buildInProgress)
        // still exist alongside buildPreview; syncPreview()/resetPreview() update both.
        buildPreview.writeToTag(tag);
    }

    @Override
    protected void readUiSyncTag(CompoundTag tag) {
        if (tag.contains("neo_storedEnergy")) storedEnergy = tag.getLong("neo_storedEnergy");
        if (tag.contains("neo_maxEnergy")) maxEnergy = tag.getLong("neo_maxEnergy");
        if (tag.contains("neo_formed")) formed = tag.getBoolean("neo_formed");
        if (tag.contains("neo_usedTypes_s")) _synUsedTypes = tag.getLong("neo_usedTypes_s");
        if (tag.contains("neo_totalTypes_s")) _synTotalTypes = tag.getLong("neo_totalTypes_s");
        if (tag.contains("neo_usedBytes_s")) _synUsedBytes = tag.getLong("neo_usedBytes_s");
        if (tag.contains("neo_totalBytes_s")) _synTotalBytes = tag.getLong("neo_totalBytes_s");
        if (tag.contains("neo_usedTypes")) usedTypes = tag.getLongArray("neo_usedTypes");
        if (tag.contains("neo_totalTypes")) totalTypes = tag.getLongArray("neo_totalTypes");
        if (tag.contains("neo_usedBytes")) usedBytes = tag.getLongArray("neo_usedBytes");
        if (tag.contains("neo_totalBytes")) totalBytes = tag.getLongArray("neo_totalBytes");
        // Build/preview state is delegated to BuildPreviewState
        // Keep individual field reads for backward compat; buildPreview syncs alongside.
        buildPreview.readFromTag(tag);
        if (tag.contains("selectedBuildLength")) selectedBuildLength = tag.getInt("selectedBuildLength");
        if (tag.contains("previewMissingBlocks")) previewMissingBlocks = tag.getInt("previewMissingBlocks");
        if (tag.contains("previewConflictBlocks")) previewConflictBlocks = tag.getInt("previewConflictBlocks");
        if (tag.contains("previewReusedBlocks")) previewReusedBlocks = tag.getInt("previewReusedBlocks");
        if (tag.contains("previewRequiredItems")) previewRequiredItems = tag.getInt("previewRequiredItems");
        if (tag.contains("previewStatusKey")) previewStatusKey = tag.getString("previewStatusKey");
        if (tag.contains("previewStatusArg1")) previewStatusArg1 = tag.getInt("previewStatusArg1");
        if (tag.contains("previewStatusArg2")) previewStatusArg2 = tag.getInt("previewStatusArg2");
        if (tag.contains("buildInProgress")) buildInProgress = tag.getBoolean("buildInProgress");
        // Safety: no build session means build cannot be in progress
        if (buildInProgress && buildSession == null) {
            buildInProgress = false;
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        tryExitInfiniteModeForDismantle();
        super.addAdditionalDrops(level, pos, drops);
        ItemStack component = infiniteStorageComponent.getStackInSlot(0);
        if (!component.isEmpty()) {
            drops.add(component.copy());
        }
    }

    private static ListTag writeUuidList(List<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }

    private static List<UUID> readUuidList(ListTag list) {
        List<UUID> ids = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            UUID id = readUuid(list.getString(i));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private ListTag writeMigrationSteps() {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> entry : migrationSteps.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("diskId", entry.getKey().toString());
            tag.putString("step", migrationStepName(entry.getValue()));
            list.add(tag);
        }
        return list;
    }

    private void readMigrationSteps(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            UUID diskId = readUuid(tag.getString("diskId"));
            if (diskId != null) {
                migrationSteps.put(diskId, migrationStepByName(tag.getString("step")));
            }
        }
    }

    private static String migrationStepName(int step) {
        return switch (step) {
            case MIGRATION_COPYING -> "COPYING";
            case MIGRATION_COMMITTED_TO_DOMAIN -> "COMMITTED_TO_DOMAIN";
            case MIGRATION_SOURCE_CLEARED -> "SOURCE_CLEARED";
            case MIGRATION_BOUND_AS_MEMBER -> "BOUND_AS_MEMBER";
            default -> "NOT_STARTED";
        };
    }

    private static int migrationStepByName(String name) {
        if (name == null || name.isBlank()) {
            return MIGRATION_NOT_STARTED;
        }
        return switch (name) {
            case "COPYING" -> MIGRATION_COPYING;
            case "COMMITTED_TO_DOMAIN" -> MIGRATION_COMMITTED_TO_DOMAIN;
            case "SOURCE_CLEARED" -> MIGRATION_SOURCE_CLEARED;
            case "BOUND_AS_MEMBER" -> MIGRATION_BOUND_AS_MEMBER;
            default -> MIGRATION_NOT_STARTED;
        };
    }

    private static byte[] writeByteSet(Set<Byte> values) {
        byte[] bytes = new byte[values.size()];
        int i = 0;
        for (byte value : values) {
            bytes[i++] = value;
        }
        return bytes;
    }

    @Nullable private static UUID readUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record ExportResult(long exported, int keysVisited) {}

    private record DomainTypeInfo(ResourceLocation typeId, int bytesPerType) {}

    private static long saturatedAdd(long left, long right) {
        if (left == Long.MAX_VALUE || right == Long.MAX_VALUE || right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
