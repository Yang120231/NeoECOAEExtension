package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.list.AEListGridWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class NEFluidHatchWidget extends NELDLibMachineWidget {
    public static final int INPUT_UI_WIDTH = 176;
    public static final int INPUT_UI_HEIGHT = 246;
    public static final int OUTPUT_UI_WIDTH = 176;
    public static final int OUTPUT_UI_HEIGHT = 210;

    private static final int PLAYER_INV_X = 7;
    private static final int NETWORK_LABEL_X = 7;
    private static final int INPUT_PANEL_Y = 30;
    private static final int OUTPUT_PANEL_Y = 28;
    private static final int HEADER_W = 112;
    private static final int HEADER_H = 28;
    private static final int HEADER_X = (INPUT_UI_WIDTH - HEADER_W) / 2;
    private static final int INPUT_HEADER_Y = 32;
    private static final int OUTPUT_HEADER_Y = 0;
    private static final int INPUT_CONFIG_Y = 0;
    private static final int INPUT_NETWORK_LABEL_Y = 63;
    private static final int INPUT_CONFIG_GRID_OFFSET_Y = 76;
    private static final int INPUT_CONFIG_X = (INPUT_UI_WIDTH - ECOFluidConfigWidget.gridWidth()) / 2;
    private static final int INPUT_CONFIG_GRID_Y = INPUT_CONFIG_Y + INPUT_CONFIG_GRID_OFFSET_Y;
    private static final int INPUT_AMOUNT_X =
            (ECOFluidConfigWidget.gridWidth() - ECOFluidConfigWidget.amountPopupWidth()) / 2;
    private static final int INPUT_AMOUNT_Y = 0;
    private static final int INPUT_INV_LABEL_Y = INPUT_CONFIG_GRID_Y + ECOFluidConfigWidget.gridHeight() + 6;
    private static final int INPUT_PLAYER_INV_Y = INPUT_INV_LABEL_Y + 12;
    private static final int INPUT_HOTBAR_Y = INPUT_PLAYER_INV_Y + NEPlayerInventoryWidgets.SLOT_SIZE * 3 + 4;
    private static final int OUTPUT_NETWORK_LABEL_Y = 29;
    private static final int OUTPUT_WAITING_LABEL_Y = 41;
    private static final int OUTPUT_LIST_X = 7;
    private static final int OUTPUT_LIST_Y = 54;
    private static final int OUTPUT_INV_LABEL_Y = 116;
    private static final int OUTPUT_PLAYER_INV_Y = OUTPUT_INV_LABEL_Y + 12;
    private static final int OUTPUT_HOTBAR_Y = OUTPUT_PLAYER_INV_Y + NEPlayerInventoryWidgets.SLOT_SIZE * 3 + 4;

    private final ECOFluidInputHatchBlockEntity inputHatch;
    private final ECOFluidOutputHatchBlockEntity outputHatch;
    private final Inventory playerInventory;

    public NEFluidHatchWidget(ECOFluidInputHatchBlockEntity hatch, Inventory playerInventory) {
        super(hatch.getBlockState().getBlock().getName(), INPUT_UI_WIDTH, INPUT_UI_HEIGHT);
        this.inputHatch = hatch;
        this.outputHatch = null;
        this.playerInventory = playerInventory;
    }

    public NEFluidHatchWidget(ECOFluidOutputHatchBlockEntity hatch, Inventory playerInventory) {
        super(hatch.getBlockState().getBlock().getName(), OUTPUT_UI_WIDTH, OUTPUT_UI_HEIGHT);
        this.inputHatch = null;
        this.outputHatch = hatch;
        this.playerInventory = playerInventory;
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected boolean shouldDrawBasePanel() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        if (inputHatch != null) {
            addWidget(new LabelWidget(NETWORK_LABEL_X, INPUT_NETWORK_LABEL_Y, this::networkStatusKey));
            addWidget(new ECOFluidConfigWidget(
                    INPUT_CONFIG_X,
                    INPUT_CONFIG_Y,
                    inputHatch.getFluids(),
                    INPUT_CONFIG_GRID_OFFSET_Y,
                    INPUT_AMOUNT_X,
                    INPUT_AMOUNT_Y,
                    true));
            NEPlayerInventoryWidgets.addPlayerInventorySlots(
                    this, playerInventory, PLAYER_INV_X, INPUT_PLAYER_INV_Y, INPUT_HOTBAR_Y);
        } else if (outputHatch != null) {
            addWidget(new LabelWidget(NETWORK_LABEL_X, OUTPUT_NETWORK_LABEL_Y, this::networkStatusKey));
            addWidget(new LabelWidget(NETWORK_LABEL_X, OUTPUT_WAITING_LABEL_Y, "gtceu.gui.waiting_list"));
            addWidget(new AEListGridWidget.Fluid(
                    OUTPUT_LIST_X, OUTPUT_LIST_Y, 3, outputHatch.getBuffer().storage()));
            NEPlayerInventoryWidgets.addPlayerInventorySlots(
                    this, playerInventory, PLAYER_INV_X, OUTPUT_PLAYER_INV_Y, OUTPUT_HOTBAR_Y);
        }
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (inputHatch != null) {
            drawChamberHeaderBackground(graphics, INPUT_HEADER_Y);
            drawChamberHeaderForeground(graphics, INPUT_HEADER_Y);
            drawBodyPanel(graphics);
            NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                    graphics, this::absX, this::absY, PLAYER_INV_X, INPUT_PLAYER_INV_Y, INPUT_HOTBAR_Y);
        } else if (outputHatch != null) {
            drawBodyPanel(graphics);
            drawChamberHeaderBackground(graphics, OUTPUT_HEADER_Y);
            NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                    graphics, this::absX, this::absY, PLAYER_INV_X, OUTPUT_PLAYER_INV_Y, OUTPUT_HOTBAR_Y);
        }
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (outputHatch != null) {
            drawChamberHeaderForeground(graphics, OUTPUT_HEADER_Y);
        }
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.common.inventory"),
                PLAYER_INV_X,
                inputHatch != null ? INPUT_INV_LABEL_Y : OUTPUT_INV_LABEL_Y,
                TEXT_MUTED);
    }

    private String networkStatusKey() {
        boolean online = inputHatch != null
                ? inputHatch.isMENetworkOnline()
                : outputHatch != null && outputHatch.isMENetworkOnline();
        return online ? "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline";
    }

    private void drawBodyPanel(GuiGraphics graphics) {
        int panelY = inputHatch != null ? INPUT_PANEL_Y : OUTPUT_PANEL_Y;
        NELDLibAe2StyleRenderer.drawAeMainPanel(
                graphics, getPositionX(), getPositionY() + panelY, width, height - panelY);
    }

    private void drawChamberHeaderBackground(GuiGraphics graphics, int y) {
        int x = absX(HEADER_X);
        int top = absY(y);
        NELDLibAe2StyleRenderer.drawAeMainPanel(graphics, x, top, HEADER_W, HEADER_H);
    }

    private void drawChamberHeaderForeground(GuiGraphics graphics, int y) {
        graphics.renderItem(chamberStack(), absX(HEADER_X + 8), absY(y + 6));
        NELDLibClientStyle.drawCenteredFitted(
                graphics, font(), title, absX(HEADER_X + 30), absY(y + 9), HEADER_W - 36, 0xFF111111);
    }

    private ItemStack chamberStack() {
        return new ItemStack((inputHatch != null ? inputHatch.getBlockState() : outputHatch.getBlockState())
                .getBlock()
                .asItem());
    }
}
