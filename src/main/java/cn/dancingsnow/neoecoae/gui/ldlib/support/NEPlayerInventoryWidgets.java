package cn.dancingsnow.neoecoae.gui.ldlib.support;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class NEPlayerInventoryWidgets {
    public static final int SLOT_SIZE = 18;

    private static final int INVENTORY_ROWS = 3;
    private static final int INVENTORY_COLUMNS = 9;
    private static final ResourceLocation VANILLA_INVENTORY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/inventory.png");
    private static final int VANILLA_INVENTORY_U = 7;
    private static final int VANILLA_INVENTORY_V = 83;
    private static final int VANILLA_INVENTORY_W = INVENTORY_COLUMNS * SLOT_SIZE;
    private static final int VANILLA_INVENTORY_H = INVENTORY_ROWS * SLOT_SIZE;
    private static final int VANILLA_HOTBAR_U = 7;
    private static final int VANILLA_HOTBAR_V = 141;
    private static final int VANILLA_HOTBAR_W = INVENTORY_COLUMNS * SLOT_SIZE;
    private static final int VANILLA_HOTBAR_H = SLOT_SIZE;
    private static final int VANILLA_TEXTURE_SIZE = 256;

    private NEPlayerInventoryWidgets() {}

    public static void addPlayerInventorySlots(
            WidgetGroup owner, Inventory inventory, int inventoryX, int inventoryY, int hotbarY) {
        for (int row = 0; row < INVENTORY_ROWS; row++) {
            for (int col = 0; col < INVENTORY_COLUMNS; col++) {
                owner.addWidget(new SlotWidget(
                                inventory,
                                col + row * INVENTORY_COLUMNS + INVENTORY_COLUMNS,
                                inventoryX + col * SLOT_SIZE,
                                inventoryY + row * SLOT_SIZE,
                                true,
                                true)
                        .setBackgroundTexture(IGuiTexture.EMPTY)
                        .setLocationInfo(true, false));
            }
        }
        for (int col = 0; col < INVENTORY_COLUMNS; col++) {
            owner.addWidget(new SlotWidget(inventory, col, inventoryX + col * SLOT_SIZE, hotbarY, true, true)
                    .setBackgroundTexture(IGuiTexture.EMPTY)
                    .setLocationInfo(true, true));
        }
    }

    public static void drawPlayerInventorySlots(
            GuiGraphics graphics,
            IntUnaryOperator screenX,
            IntUnaryOperator screenY,
            int inventoryBgX,
            int inventoryBgY,
            int hotbarBgY) {
        graphics.blit(
                VANILLA_INVENTORY_TEXTURE,
                screenX.applyAsInt(inventoryBgX),
                screenY.applyAsInt(inventoryBgY),
                VANILLA_INVENTORY_W,
                VANILLA_INVENTORY_H,
                VANILLA_INVENTORY_U,
                VANILLA_INVENTORY_V,
                VANILLA_INVENTORY_W,
                VANILLA_INVENTORY_H,
                VANILLA_TEXTURE_SIZE,
                VANILLA_TEXTURE_SIZE);
        graphics.blit(
                VANILLA_INVENTORY_TEXTURE,
                screenX.applyAsInt(inventoryBgX),
                screenY.applyAsInt(hotbarBgY),
                VANILLA_HOTBAR_W,
                VANILLA_HOTBAR_H,
                VANILLA_HOTBAR_U,
                VANILLA_HOTBAR_V,
                VANILLA_HOTBAR_W,
                VANILLA_HOTBAR_H,
                VANILLA_TEXTURE_SIZE,
                VANILLA_TEXTURE_SIZE);
    }

    public static void drawVanillaSlot(GuiGraphics graphics, int x, int y) {
        graphics.blit(
                VANILLA_INVENTORY_TEXTURE,
                x,
                y,
                SLOT_SIZE,
                SLOT_SIZE,
                VANILLA_INVENTORY_U,
                VANILLA_INVENTORY_V,
                SLOT_SIZE,
                SLOT_SIZE,
                VANILLA_TEXTURE_SIZE,
                VANILLA_TEXTURE_SIZE);
    }

    public static void drawVanillaSlotColumn(GuiGraphics graphics, int x, int y, int rows) {
        for (int row = 0; row < rows; row++) {
            drawVanillaSlot(graphics, x, y + row * SLOT_SIZE);
        }
    }
}
