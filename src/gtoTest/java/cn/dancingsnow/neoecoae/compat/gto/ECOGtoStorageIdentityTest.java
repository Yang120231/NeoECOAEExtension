package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.persistence.StorageAmounts;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Storage tables keyed by GTO's interned AEKeys, and the change version GTO's StorageService relies on. */
class ECOGtoStorageIdentityTest {
    /** A real AEKey, so the fork assigns its identity hash and mix as for interned item/fluid keys. */
    static final class Key extends AEKey {
        @Override public AEKeyType getType() { return null; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() { return new CompoundTag(); }
        @Override public Object getPrimaryKey() { return this; }
        @Override public ResourceLocation getId() { return null; }
        @Override public void writeToPacket(FriendlyByteBuf buf) {}
        @Override protected Component computeDisplayName() { return Component.empty(); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }

    @Test void identityTableKeepsExactAmountsAcrossTheLongBoundary() {
        var amounts = new StorageAmounts();
        AEKey a = new Key(), b = new Key();
        amounts.setLong(a, 5);
        amounts.add(a, Long.MAX_VALUE);
        assertTrue(amounts.isBig(a));
        assertEquals(Long.MAX_VALUE, amounts.visible(a));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(5)), amounts.get(a).toBigInteger());
        assertEquals(Long.MAX_VALUE - 1, amounts.subtract(a, Long.MAX_VALUE, 6));
        assertFalse(amounts.isBig(a));
        amounts.set(b, HugeAmount.of(BigInteger.TEN));
        assertEquals(2, amounts.size());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE - 1).add(BigInteger.TEN), amounts.totalExact());
        amounts.subtract(b, 10);
        assertEquals(1, amounts.size());
        assertEquals(0, amounts.visible(b));
        assertFalse(amounts.visibleAmounts().containsKey(b));
    }

    @Test void everyMutationAdvancesTheContentRevision() {
        var amounts = new StorageAmounts();
        AEKey key = new Key();
        long revision = amounts.contentRevision();
        amounts.setLong(key, 3);
        assertNotEquals(revision, revision = amounts.contentRevision());
        amounts.setLong(key, 3);
        assertEquals(revision, amounts.contentRevision(), "An unchanged amount is not a change");
        amounts.add(key, 1);
        assertNotEquals(revision, revision = amounts.contentRevision());
        amounts.subtract(key, 4);
        assertNotEquals(revision, amounts.contentRevision());
    }

    @Test void changeVersionIsStableUntilTheListingChanges() {
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        var enabled = new AtomicBoolean(true);
        var storage = new ECOInfiniteStorage(engine, Component.empty(), enabled::get);
        AEKey a = new Key(), b = new Key();
        assertEquals(7, storage.insert(a, 7, Actionable.MODULATE, IActionSource.empty()));

        long version = storage.getChangeVersion();
        assertTrue(version >= 0, "A known version lets GTO skip re-listing");
        assertEquals(version, storage.getChangeVersion());
        storage.insert(b, 1, Actionable.SIMULATE, IActionSource.empty());
        storage.extract(a, 1, Actionable.SIMULATE, IActionSource.empty());
        assertEquals(version, storage.getChangeVersion(), "Simulations do not change the listing");

        storage.insert(b, 2, Actionable.MODULATE, IActionSource.empty());
        long afterInsert = storage.getChangeVersion();
        assertNotEquals(version, afterInsert);
        storage.extract(a, 7, Actionable.MODULATE, IActionSource.empty());
        assertNotEquals(afterInsert, storage.getChangeVersion());

        var listing = new KeyCounter();
        storage.getAvailableStacks(listing);
        assertEquals(0, listing.get(a));
        assertEquals(2, listing.get(b));

        enabled.set(false);
        assertEquals(-1L, storage.getChangeVersion(), "A disabled view is re-listed (empty) every refresh");
    }
}
