package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.Ae2CraftingCpuFastPathMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.mockito.Mockito.*;

class ECOExternalCpuCompletionTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }


}
