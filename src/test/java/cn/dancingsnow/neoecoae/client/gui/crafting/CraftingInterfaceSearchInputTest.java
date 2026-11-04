package cn.dancingsnow.neoecoae.client.gui.crafting;

import cn.dancingsnow.neoecoae.gui.crafting.CraftingInterfaceUI;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftingInterfaceSearchInputTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var modList = mockStatic(ModList.class)) {
            var mods = mock(ModList.class);
            when(mods.getAllScanData()).thenReturn(List.of());
            modList.when(ModList::get).thenReturn(mods);
            Class.forName("cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement");
        }
    }


}
