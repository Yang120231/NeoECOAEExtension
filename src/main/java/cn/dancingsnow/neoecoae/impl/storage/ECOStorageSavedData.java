package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import com.mojang.logging.LogUtils;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class ECOStorageSavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = NeoECOAE.MOD_ID + "_storage_domains";
    private static final int DATA_VERSION = 1;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final Map<UUID, StoredContents> disks = new HashMap<>();
    private final Map<UUID, StoredContents> domains = new HashMap<>();
    private final Map<UUID, MigrationRecord> migrations = new HashMap<>();

    public static ECOStorageSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ECOStorageSavedData::load, ECOStorageSavedData::new, DATA_NAME);
    }

    public static ECOStorageSavedData load(CompoundTag tag) {
        ECOStorageSavedData data = new ECOStorageSavedData();
        data.readContentsMap(tag.getList("disks", Tag.TAG_COMPOUND), data.disks);
        data.readContentsMap(tag.getList("domains", Tag.TAG_COMPOUND), data.domains);
        data.readMigrationMap(tag.getList("migrations", Tag.TAG_COMPOUND));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("disks", writeContentsMap(disks));
        tag.put("domains", writeContentsMap(domains));
        tag.put("migrations", writeMigrationMap());
        return tag;
    }

    public StoredContents getOrCreateDisk(UUID diskId) {
        return disks.computeIfAbsent(diskId, ignored -> new StoredContents());
    }

    public StoredContents getOrCreateDomain(UUID domainId) {
        return domains.computeIfAbsent(domainId, ignored -> new StoredContents());
    }

    public StoredContents getDiskOrEmpty(UUID diskId) {
        return disks.getOrDefault(diskId, StoredContents.EMPTY);
    }

    public StoredContents getDomainOrEmpty(UUID domainId) {
        return domains.getOrDefault(domainId, StoredContents.EMPTY);
    }

    public void importLegacyDiskContents(UUID diskId, List<GenericStack> legacyStacks) {
        if (legacyStacks.isEmpty()) {
            getOrCreateDisk(diskId);
            return;
        }
        StoredContents disk = getOrCreateDisk(diskId);
        if (!disk.isEmpty()) {
            return;
        }
        for (GenericStack stack : legacyStacks) {
            disk.add(stack.what(), BigInteger.valueOf(stack.amount()));
        }
        setDirty();
    }

    public void commitDiskToDomain(UUID domainId, UUID diskId) {
        StoredContents domain = getOrCreateDomain(domainId);
        if (domain.isSourceCommitted(diskId)) {
            return;
        }
        StoredContents disk = getDiskOrEmpty(diskId);
        for (Map.Entry<AEKey, BigInteger> entry : disk.amounts().entrySet()) {
            domain.add(entry.getKey(), entry.getValue());
        }
        domain.markSourceCommitted(diskId);
        setDirty();
    }

    public void clearDisk(UUID diskId) {
        StoredContents removed = disks.remove(diskId);
        if (removed != null
                && (!removed.isEmpty() || !removed.committedSources().isEmpty())) {
            setDirty();
        }
    }

    public void removeDomain(UUID domainId) {
        StoredContents removed = domains.remove(domainId);
        MigrationRecord migration = migrations.remove(domainId);
        if (removed != null || migration != null) {
            setDirty();
        }
    }

    public void beginMigration(UUID domainId, List<UUID> diskIds) {
        MigrationRecord existing = migrations.get(domainId);
        if (existing != null && existing.diskIds().equals(diskIds)) {
            return;
        }
        migrations.put(domainId, new MigrationRecord(diskIds));
        getOrCreateDomain(domainId);
        setDirty();
    }

    public void finishMigration(UUID domainId) {
        if (migrations.remove(domainId) != null) {
            setDirty();
        }
    }

    public boolean isDomainEmpty(UUID domainId) {
        return getDomainOrEmpty(domainId).isEmpty();
    }

    private void readContentsMap(ListTag list, Map<UUID, StoredContents> target) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ownerTag = list.getCompound(i);
            UUID id = readUuid(ownerTag.getString("id"));
            if (id == null) {
                continue;
            }
            StoredContents contents = StoredContents.read(ownerTag);
            if (!contents.isEmpty() || !contents.committedSources().isEmpty()) {
                target.put(id, contents);
            }
        }
    }

    private ListTag writeContentsMap(Map<UUID, StoredContents> source) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, StoredContents> entry : source.entrySet()) {
            if (entry.getValue().isEmpty()
                    && entry.getValue().committedSources().isEmpty()) {
                continue;
            }
            CompoundTag ownerTag = entry.getValue().write();
            ownerTag.putString("id", entry.getKey().toString());
            list.add(ownerTag);
        }
        return list;
    }

    private void readMigrationMap(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            UUID domainId = readUuid(tag.getString("domainId"));
            if (domainId == null) {
                continue;
            }
            migrations.put(domainId, MigrationRecord.read(tag));
        }
    }

    private ListTag writeMigrationMap() {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, MigrationRecord> entry : migrations.entrySet()) {
            CompoundTag tag = entry.getValue().write();
            tag.putString("domainId", entry.getKey().toString());
            list.add(tag);
        }
        return list;
    }

    private static UUID readUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static long saturate(BigInteger value) {
        if (value.signum() <= 0) {
            return 0L;
        }
        return value.compareTo(LONG_MAX) >= 0 ? Long.MAX_VALUE : value.longValue();
    }

    public static final class StoredContents {
        private static final StoredContents EMPTY = new StoredContents(Map.of(), Set.of());
        private final Map<AEKey, BigInteger> amounts;
        private final Set<UUID> committedSources;

        public StoredContents() {
            this(new HashMap<>(), new HashSet<>());
        }

        private StoredContents(Map<AEKey, BigInteger> amounts, Set<UUID> committedSources) {
            this.amounts = amounts;
            this.committedSources = committedSources;
        }

        public Map<AEKey, BigInteger> amounts() {
            return amounts;
        }

        public Set<UUID> committedSources() {
            return committedSources;
        }

        public boolean isEmpty() {
            return amounts.isEmpty();
        }

        public BigInteger get(AEKey key) {
            return amounts.getOrDefault(key, BigInteger.ZERO);
        }

        public void set(AEKey key, BigInteger amount) {
            if (amount.signum() <= 0) {
                amounts.remove(key);
            } else {
                amounts.put(key, amount);
            }
        }

        public void add(AEKey key, BigInteger amount) {
            if (amount.signum() <= 0) {
                return;
            }
            set(key, get(key).add(amount));
        }

        public void subtract(AEKey key, BigInteger amount) {
            if (amount.signum() <= 0) {
                return;
            }
            set(key, get(key).subtract(amount));
        }

        public int storedTypes() {
            return amounts.size();
        }

        public long storedAmountSaturated() {
            BigInteger total = BigInteger.ZERO;
            for (BigInteger amount : amounts.values()) {
                total = total.add(amount);
                if (total.compareTo(LONG_MAX) >= 0) {
                    return Long.MAX_VALUE;
                }
            }
            return total.longValue();
        }

        public boolean isSourceCommitted(UUID diskId) {
            return committedSources.contains(diskId);
        }

        public void markSourceCommitted(UUID diskId) {
            committedSources.add(diskId);
        }

        private CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            ListTag contentList = new ListTag();
            for (Map.Entry<AEKey, BigInteger> entry : amounts.entrySet()) {
                if (entry.getValue().signum() <= 0) {
                    continue;
                }
                CompoundTag stackTag = new CompoundTag();
                stackTag.put("key", entry.getKey().toTagGeneric());
                stackTag.putString("amount", entry.getValue().toString());
                contentList.add(stackTag);
            }
            tag.put("contents", contentList);

            ListTag committed = new ListTag();
            for (UUID diskId : committedSources) {
                committed.add(StringTag.valueOf(diskId.toString()));
            }
            tag.put("committedSources", committed);
            return tag;
        }

        private static StoredContents read(CompoundTag tag) {
            StoredContents contents = new StoredContents();
            ListTag contentList = tag.getList("contents", Tag.TAG_COMPOUND);
            for (int i = 0; i < contentList.size(); i++) {
                CompoundTag stackTag = contentList.getCompound(i);
                try {
                    AEKey key = AEKey.fromTagGeneric(stackTag.getCompound("key"));
                    BigInteger amount = new BigInteger(stackTag.getString("amount"));
                    if (key != null && amount.signum() > 0) {
                        contents.set(key, amount);
                    }
                } catch (RuntimeException ex) {
                    LOGGER.warn("Failed to read ECO storage entry from SavedData: {}", stackTag, ex);
                }
            }

            ListTag committed = tag.getList("committedSources", Tag.TAG_STRING);
            for (int i = 0; i < committed.size(); i++) {
                UUID diskId = readUuid(committed.getString(i));
                if (diskId != null) {
                    contents.markSourceCommitted(diskId);
                }
            }
            return contents;
        }
    }

    public record MigrationRecord(List<UUID> diskIds) {
        private CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            ListTag disks = new ListTag();
            for (UUID diskId : diskIds) {
                disks.add(StringTag.valueOf(diskId.toString()));
            }
            tag.put("diskIds", disks);
            return tag;
        }

        private static MigrationRecord read(CompoundTag tag) {
            ListTag diskList = tag.getList("diskIds", Tag.TAG_STRING);
            List<UUID> ids = new java.util.ArrayList<>(diskList.size());
            for (int i = 0; i < diskList.size(); i++) {
                UUID diskId = readUuid(diskList.getString(i));
                if (diskId != null) {
                    ids.add(diskId);
                }
            }
            return new MigrationRecord(List.copyOf(ids));
        }
    }
}
