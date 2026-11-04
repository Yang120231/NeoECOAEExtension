package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(cn.dancingsnow.neoecoae.util.GtoNativeKeyExtension.class)
class LargeWorkstationBatchPersistenceTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void rebatchedFractionalProgressSurvivesReloadAndRejectsInvalidRemainders() throws Exception {
        try (var keys = mockStatic(AEKeyTypes.class);
             var internal = mockStatic(appeng.api.stacks.AEKeyTypesInternal.class)) {
            internal.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
                .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
            keys.when(() -> AEKeyTypes.get(AEKeyType.fluids().getId())).thenReturn(AEKeyType.fluids());
            var type = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
            var constructor = type.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            var owned = new KeyCounter();
            owned.add(AEFluidKey.of(Fluids.WATER), 1_048_576);
            var output = new KeyCounter();
            output.add(AEFluidKey.of(Fluids.LAVA), 262_144);
            Object batch = constructor.newInstance(262_144L, 1_000L, 9, 1, owned, output,
                null, new ResourceLocation("test:rebatched"), null, null, 123L);
            var progress = type.getDeclaredField("progress");
            progress.setAccessible(true);
            progress.set(batch, 1);
            var remainder = type.getDeclaredField("progressRemainder");
            remainder.setAccessible(true);
            remainder.set(batch, 196_601L);
            var save = type.getDeclaredMethod("save");
            save.setAccessible(true);
            var load = type.getDeclaredMethod("load", CompoundTag.class, long.class);
            load.setAccessible(true);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            CompoundTag tag = (CompoundTag) save.invoke(batch);
            var restored = load.invoke(null, tag, 234L);
            assertNotNull(restored);
            assertEquals(tag, save.invoke(restored));
            assertEquals(65_535L * 7, (long) progress.getInt(restored) * 262_144 + remainder.getLong(restored));

            tag.putInt("processingTicks", 7);
            tag.putLong("fePerCraft", 9_007_199_254_740_993L);
            tag.putLong("feRemainder", 5);
            tag.putBoolean("legacyTiming", false);
            var timed = load.invoke(null, tag, 234L);
            assertNotNull(timed);
            assertEquals(tag, save.invoke(timed), "Duration, selected energy source and fractional FE survive reload");
            tag.putLong("feRemainder", 7);
            assertNull(load.invoke(null, tag, 234L));
            tag.putLong("feRemainder", 5);

            tag.putLong("progressRemainder", -1);
            assertNull(load.invoke(null, tag, 234L));
            tag.putLong("progressRemainder", 262_144);
            assertNull(load.invoke(null, tag, 234L));
            tag.putInt("progress", 200);
            tag.putLong("progressRemainder", 1);
            assertNull(load.invoke(null, tag, 234L));
        }
    }

}
