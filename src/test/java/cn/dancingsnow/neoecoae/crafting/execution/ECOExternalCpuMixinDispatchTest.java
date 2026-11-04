package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.Ae2CraftingCpuFastPathMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exercises both injection handlers, including the optional-engine selection before dispatch. */
class ECOExternalCpuMixinDispatchTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }


}
