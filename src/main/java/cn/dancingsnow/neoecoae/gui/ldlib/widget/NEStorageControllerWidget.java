package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.client.gui.Icon;
import appeng.core.localization.GuiText;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuLocators;
import cn.dancingsnow.neoecoae.api.ECOStorageTypeLimits;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeItemTransfer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibValueText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPlayerInventoryWidgets;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEStorageMetricsModel.Metric;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEStorageMetricsModel.StorageMetrics;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class NEStorageControllerWidget extends NELDLibSyncedStateWidget<NEStorageUiState> {
    public static final int UI_WIDTH = 344;
    private static final int UI_HEIGHT = 252;
    private static final int LEFT_PANEL_X = 8;
    private static final int LEFT_PANEL_Y = 24;
    private static final int LEFT_PANEL_W = 218;
    private static final int LEFT_PANEL_H = 132;
    private static final int TEXT_START_X = LEFT_PANEL_X + 8;
    private static final int TEXT_START_Y = LEFT_PANEL_Y + 8;
    private static final int TEXT_LINE_STEP = 13;
    private static final int TEXT_MAX_W = LEFT_PANEL_W - 16;
    private static final int INFINITE_INFO_RESERVED_H = 32;
    private static final int PRIORITY_BUTTON_X = UI_WIDTH - 22;
    private static final int PRIORITY_BUTTON_Y = 0;
    private static final int PRIORITY_BUTTON_W = 22;
    private static final int PRIORITY_BUTTON_H = 22;
    static final int SLOT_SIZE = 18;
    private static final int INFINITE_SLOT_X = LEFT_PANEL_X + LEFT_PANEL_W - SLOT_SIZE - 8;
    private static final int INFINITE_SLOT_Y = LEFT_PANEL_Y + LEFT_PANEL_H - SLOT_SIZE - 8;
    private static final int HEADER_STATUS_RIGHT = PRIORITY_BUTTON_X - 6;
    static final int PLAYER_INV_X = LEFT_PANEL_X;
    private static final int PLAYER_INV_LABEL_Y = 159;
    static final int PLAYER_INV_Y = 171;
    static final int PLAYER_HOTBAR_Y = 229;
    private static final double MATRIX_SCROLL_SPEED = 18.0D;
    private static final double LEFT_SCROLL_SPEED = 13.0D;
    private static final double ANIMATION_SPEED = 0.16D;

    private static final Map<ScrollKey, ScrollSnapshot> SCROLL_MEMORY = new HashMap<>();

    private final ECOStorageSystemBlockEntity storage;
    private final Player player;
    private final Inventory playerInventory;
    private final NEStorageMatrixPanel matrixPanel;
    private final NEStorageMetricColumnPanel metricColumnPanel;
    private final boolean showInfiniteStorage;
    private final boolean showTypeTotals;

    private double animatedEnergyPct;
    private final Map<String, Double> animatedTypePct = new HashMap<>();
    private double matrixScrollPixels;
    private double matrixScrollTargetPixels;
    private boolean matrixScrollbarDragging;
    private double metricScrollPixels;
    private double metricScrollTargetPixels;
    private boolean metricScrollbarDragging;
    private double leftScrollPixels;

    public NEStorageControllerWidget(ECOStorageSystemBlockEntity storage, Player player) {
        super(
                storage.getBlockState().getBlock().getName(),
                UI_WIDTH,
                uiHeight(storage),
                NEStorageUiState.empty(storage.getBlockPos()),
                storage::createStorageUiState,
                NELDLibStateCodecs::writeStorage,
                NELDLibStateCodecs::readStorage,
                20);
        this.storage = storage;
        this.player = player;
        this.playerInventory = player.getInventory();
        this.showInfiniteStorage = storage.canUseInfiniteStorageComponents();
        this.showTypeTotals = ECOStorageTypeLimits.hasFiniteLimit(storage.getTier().getTier());
        this.matrixPanel = new NEStorageMatrixPanel(
                this::font,
                this::absX,
                this::absY,
                () -> matrixScrollPixels,
                () -> matrixScrollTargetPixels,
                value -> matrixScrollPixels = value,
                value -> matrixScrollTargetPixels = value,
                this::rememberScrollState);
        this.metricColumnPanel = new NEStorageMetricColumnPanel(
                this::font,
                this::absX,
                this::absY,
                () -> metricScrollPixels,
                () -> metricScrollTargetPixels,
                value -> metricScrollPixels = value,
                value -> metricScrollTargetPixels = value,
                this::rememberScrollState);
        restoreScrollState();
    }

    public static int uiHeight(ECOStorageSystemBlockEntity storage) {
        return UI_HEIGHT;
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new NEAe2IconButtonWidget(
                        PRIORITY_BUTTON_X,
                        PRIORITY_BUTTON_Y,
                        PRIORITY_BUTTON_W,
                        PRIORITY_BUTTON_H,
                        Icon.WRENCH,
                        click -> {
                            if (!click.isRemote && player instanceof ServerPlayer serverPlayer && storage.isFormed()) {
                                MenuOpener.open(PriorityMenu.TYPE, serverPlayer, MenuLocators.forBlockEntity(storage));
                            }
                        })
                .useAeTabButton());
        if (showInfiniteStorage) {
            addWidget(new SlotWidget(
                            new NEForgeItemTransfer(storage.getInfiniteStorageComponentInventory(), null),
                            0,
                            INFINITE_SLOT_X,
                            INFINITE_SLOT_Y,
                            true,
                            true)
                    .setBackgroundTexture(IGuiTexture.EMPTY));
        }
        NEPlayerInventoryWidgets.addPlayerInventorySlots(
                this, playerInventory, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        restoreScrollState();
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        StorageMetrics metrics = NEStorageMetricsModel.from(currentState());
        animatedEnergyPct = animateTo(animatedEnergyPct, metrics.energy().percent());
        animatedTypePct.keySet().removeIf(key -> metrics.types().stream()
                .noneMatch(metric -> metric.key().equals(key)));

        int ox = getPositionX();
        int oy = getPositionY();
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + LEFT_PANEL_X, oy + LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H);
        if (showInfiniteStorage) {
            NELDLibAe2StyleRenderer.drawAeSlot(graphics, absX(INFINITE_SLOT_X), absY(INFINITE_SLOT_Y));
        }

        List<Metric> columns = NEStorageMetricsModel.columnMetrics(metrics);
        double[] values = new double[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            Metric metric = columns.get(i);
            double value = animateTo(animatedTypePct.getOrDefault(metric.key(), 0.0D), metric.percent());
            animatedTypePct.put(metric.key(), value);
            values[i] = value;
        }
        metricColumnPanel.draw(graphics, columns, values, metricScrollbarDragging);

        NEPlayerInventoryWidgets.drawPlayerInventorySlots(
                graphics, this::absX, this::absY, PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y);
        matrixPanel.draw(graphics, currentState(), mouseX, mouseY, matrixScrollbarDragging);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        StorageMetrics metrics = NEStorageMetricsModel.from(currentState());
        drawLocalString(graphics, title, NELDLibUiTitleX(), NELDLibUiTitleY(), TEXT_PRIMARY);
        drawStorageTextLines(graphics, metrics);
        drawInfiniteStorageStatus(graphics, currentState());
        drawLeftScrollbar(graphics, metrics);
        drawFormedStatus(graphics, currentState().formed());
        drawLocalString(
                graphics,
                Component.translatable("gui.neoecoae.common.inventory"),
                PLAYER_INV_X,
                PLAYER_INV_LABEL_Y,
                TEXT_MUTED);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(PRIORITY_BUTTON_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_W, PRIORITY_BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font(), List.of(GuiText.Priority.text()), mouseX, mouseY);
            return;
        }
        if (matrixPanel.renderTooltip(graphics, currentState(), mouseX, mouseY)) {
            return;
        }
        if (renderInfiniteStorageTooltip(graphics, currentState(), mouseX, mouseY)) {
            return;
        }
        metricColumnPanel.renderTooltip(
                graphics,
                NEStorageMetricsModel.columnMetrics(NEStorageMetricsModel.from(currentState())),
                mouseX,
                mouseY);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (Widget.isMouseOver(absX(LEFT_PANEL_X), absY(LEFT_PANEL_Y), LEFT_PANEL_W, LEFT_PANEL_H, mouseX, mouseY)) {
            double maxScroll = maxLeftScrollPixels();
            double previous = leftScrollPixels;
            leftScrollPixels = Mth.clamp(leftScrollPixels - wheelDelta * LEFT_SCROLL_SPEED, 0.0D, maxScroll);
            rememberScrollState();
            return leftScrollPixels != previous || maxScroll > 0.0D;
        }
        if (metricColumnPanel.isMouseInPanel(mouseX, mouseY)) {
            List<Metric> columns = NEStorageMetricsModel.columnMetrics(NEStorageMetricsModel.from(currentState()));
            return metricColumnPanel.scrollBy(columns, wheelDelta);
        }
        if (Widget.isMouseOver(
                absX(NEStorageMatrixPanel.panelX()),
                absY(NEStorageMatrixPanel.panelY()),
                NEStorageMatrixPanel.panelW(),
                NEStorageMatrixPanel.panelH(),
                mouseX,
                mouseY)) {
            double oldTarget = matrixScrollTargetPixels;
            matrixScrollTargetPixels = Mth.clamp(
                    matrixScrollTargetPixels - wheelDelta * MATRIX_SCROLL_SPEED,
                    0.0D,
                    matrixPanel.maxScrollPixels(currentState()));
            rememberScrollState();
            return matrixScrollTargetPixels != oldTarget || matrixPanel.maxScrollPixels(currentState()) > 0.0D;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
                && matrixPanel.maxScrollPixels(currentState()) > 0.0D
                && Widget.isMouseOver(
                        absX(NEStorageMatrixPanel.viewX()),
                        absY(NEStorageMatrixPanel.scrollbarY()),
                        NEStorageMatrixPanel.viewW(),
                        NEStorageMatrixPanel.scrollbarH(),
                        mouseX,
                        mouseY)) {
            matrixScrollbarDragging = true;
            matrixPanel.updateScrollFromMouse(currentState(), mouseX);
            return true;
        }
        if (button == 0
                && metricColumnPanel.isMouseInScrollbar(
                        NEStorageMetricsModel.columnMetrics(NEStorageMetricsModel.from(currentState())),
                        mouseX,
                        mouseY)) {
            metricScrollbarDragging = true;
            metricColumnPanel.updateScrollFromMouse(
                    mouseX, NEStorageMetricsModel.columnMetrics(NEStorageMetricsModel.from(currentState())));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && matrixScrollbarDragging) {
            matrixPanel.updateScrollFromMouse(currentState(), mouseX);
            return true;
        }
        if (button == 0 && metricScrollbarDragging) {
            metricColumnPanel.updateScrollFromMouse(
                    mouseX, NEStorageMetricsModel.columnMetrics(NEStorageMetricsModel.from(currentState())));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && matrixScrollbarDragging) {
            matrixScrollbarDragging = false;
            rememberScrollState();
            return true;
        }
        if (button == 0 && metricScrollbarDragging) {
            metricScrollbarDragging = false;
            rememberScrollState();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawStorageTextLines(GuiGraphics g, StorageMetrics metrics) {
        leftScrollPixels = Mth.clamp(leftScrollPixels, 0.0D, maxLeftScrollPixels(metrics));
        int x = absX(TEXT_START_X);
        int y = absY(TEXT_START_Y - (int) Math.round(leftScrollPixels));
        g.enableScissor(
                absX(LEFT_PANEL_X + 4),
                absY(LEFT_PANEL_Y + 4),
                absX(LEFT_PANEL_X + LEFT_PANEL_W - 4),
                absY(LEFT_PANEL_Y + leftPanelTextViewportHeight() + 4));

        int lineStep = TEXT_LINE_STEP;
        drawPlainLine(g, Component.translatable("gui.neoecoae.storage.energy"), x, y, NELDLibStyle.DARK_TEXT_PRIMARY);
        y += lineStep;
        NELDLibValueText.drawUsedTotal(
                g,
                font(),
                Component.translatable("gui.neoecoae.storage.energy_storage").getString() + ": ",
                NELDLibText.number(Math.max(0L, metrics.energy().used())),
                NELDLibText.number(Math.max(0L, metrics.energy().max())),
                metrics.energy().used(),
                metrics.energy().max(),
                "AE",
                x,
                y);
        y += lineStep;

        for (Metric metric : metrics.types()) {
            y = drawStorageTypeBlock(g, metric, x, y, lineStep);
        }
        g.disableScissor();
    }

    private double maxLeftScrollPixels() {
        return maxLeftScrollPixels(NEStorageMetricsModel.from(currentState()));
    }

    private double maxLeftScrollPixels(StorageMetrics metrics) {
        int typeCount = metrics.types().size();
        int lineCount = 2 + typeCount * 3;
        int contentHeight = (lineCount - 1) * TEXT_LINE_STEP + font().lineHeight;
        int viewportHeight = leftPanelTextViewportHeight() - 8;
        return Math.max(0, contentHeight - viewportHeight);
    }

    private void drawLeftScrollbar(GuiGraphics g, StorageMetrics metrics) {
        double maxScroll = maxLeftScrollPixels(metrics);
        if (maxScroll <= 0.0D) {
            leftScrollPixels = 0.0D;
            return;
        }
        leftScrollPixels = Mth.clamp(leftScrollPixels, 0.0D, maxScroll);
        int trackX = absX(LEFT_PANEL_X + LEFT_PANEL_W - 5);
        int trackY = absY(LEFT_PANEL_Y + 5);
        int trackH = leftPanelTextViewportHeight() - 10;
        int contentH = trackH + (int) Math.ceil(maxScroll);
        int thumbH = Math.max(12, trackH * trackH / contentH);
        int thumbY = trackY + (int) Math.round((trackH - thumbH) * leftScrollPixels / maxScroll);
        g.fill(trackX, trackY, trackX + 2, trackY + trackH, 0xAA17141E);
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF8B83A0);
    }

    private void drawInfiniteStorageStatus(GuiGraphics graphics, NEStorageUiState state) {
        if (!showInfiniteStorage) {
            return;
        }
        Component status = state.infiniteStorageUnlocked()
                ? Component.translatable("gui.neoecoae.storage.infinite_ready")
                : Component.translatable(
                        "gui.neoecoae.storage.infinite_waiting_component",
                        state.infiniteComponentCount(),
                        state.requiredInfiniteComponentCount());
        Component fitted = cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibTextRender.truncateWithEllipsis(
                font(), status, INFINITE_SLOT_X - LEFT_PANEL_X - 14);
        drawPlainLine(
                graphics,
                fitted,
                absX(LEFT_PANEL_X + 8),
                absY(LEFT_PANEL_Y + LEFT_PANEL_H - 18),
                state.infiniteStorageUnlocked() ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_MUTED);
    }

    private int leftPanelTextViewportHeight() {
        return LEFT_PANEL_H - (showInfiniteStorage ? INFINITE_INFO_RESERVED_H : 0);
    }

    private boolean useCompactLeftText() {
        return showInfiniteStorage;
    }

    private int leftTextLineStep() {
        return TEXT_LINE_STEP;
    }

    private boolean renderInfiniteStorageTooltip(
            GuiGraphics graphics, NEStorageUiState state, int mouseX, int mouseY) {
        if (!showInfiniteStorage
                || !isMouseIn(INFINITE_SLOT_X, INFINITE_SLOT_Y, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
            return false;
        }
        graphics.renderComponentTooltip(
                font(),
                List.of(
                        Component.translatable("gui.neoecoae.storage.infinite_slot.tooltip"),
                        Component.translatable(
                                "gui.neoecoae.storage.infinite_slot.component",
                                state.infiniteComponentCount() + " / " + state.requiredInfiniteComponentCount()),
                        Component.translatable(
                                "gui.neoecoae.storage.infinite_slot.l9",
                                state.l9MatrixDriveCount(),
                                state.requiredL9MatrixDriveCount())),
                mouseX,
                mouseY);
        return true;
    }

    private int drawStorageTypeBlock(GuiGraphics g, Metric metric, int x, int y, int lineStep) {
        drawPlainLine(g, metric.label(), x, y, metric.accentColor());
        y += lineStep;
        String typeSuffix = Component.translatable("gui.neoecoae.common.types").getString();
        if (showTypeTotals) {
            NELDLibValueText.drawUsedTotal(
                    g,
                    font(),
                    "",
                    metric.usedTypesText(),
                    metric.totalTypesText(),
                    metric.usedTypes(),
                    metric.totalTypes(),
                    typeSuffix,
                    x,
                    y);
        } else {
            NELDLibValueText.drawUsedOnly(
                    g, font(), metric.usedTypesText(), metric.usedTypes(), typeSuffix, x, y);
        }
        y += lineStep;
        drawByteUsedTotalLine(g, metric, x, y);
        return y + lineStep;
    }

    private void drawByteUsedTotalLine(GuiGraphics g, Metric metric, int x, int y) {
        String usedText = metric.usedText();
        String maxText = metric.maxText();
        String suffix =
                Component.translatable("gui.neoecoae.storage.bytes_used").getString();
        if (font().width(usedText + " / " + maxText + " " + suffix) > TEXT_MAX_W) {
            suffix = Component.translatable("gui.neoecoae.storage.used_short").getString();
        }
        NELDLibValueText.drawUsedTotal(g, font(), "", usedText, maxText, metric.used(), metric.max(), suffix, x, y);
    }

    private void drawFormedStatus(GuiGraphics g, boolean formed) {
        Component label = Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component value = boolText(formed);
        int textW = font().width(label) + font().width(value);
        int textX = absX(HEADER_STATUS_RIGHT - textW);
        int textY = absY(NELDLibUiTitleY());
        g.drawString(font(), label, textX, textY, 0xFF4A4A4A, false);
        g.drawString(font(), value, textX + font().width(label), textY, formed ? 0xFF1F9D55 : 0xFFD13F3F, false);
    }

    private void drawPlainLine(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font(), text, x, y, color, false);
    }

    private int NELDLibUiTitleX() {
        return 8;
    }

    private int NELDLibUiTitleY() {
        return 8;
    }

    private void restoreScrollState() {
        scrollKey().map(SCROLL_MEMORY::get).ifPresent(snapshot -> {
            leftScrollPixels = snapshot.leftScrollPixels();
            metricScrollPixels = snapshot.metricScrollPixels();
            metricScrollTargetPixels = snapshot.metricScrollPixels();
            matrixScrollPixels = snapshot.matrixScrollPixels();
            matrixScrollTargetPixels = snapshot.matrixScrollPixels();
        });
    }

    private void rememberScrollState() {
        scrollKey()
                .ifPresent(key -> SCROLL_MEMORY.put(
                        key,
                        new ScrollSnapshot(
                                leftScrollPixels,
                                metricScrollbarDragging ? metricScrollPixels : metricScrollTargetPixels,
                                matrixScrollbarDragging ? matrixScrollPixels : matrixScrollTargetPixels)));
    }

    private Optional<ScrollKey> scrollKey() {
        if (storage.getLevel() == null) {
            return Optional.empty();
        }
        return Optional.of(new ScrollKey(
                player.getUUID(),
                storage.getLevel().dimension().location(),
                storage.getBlockPos().immutable()));
    }

    private static double animateTo(double current, double target) {
        double start = current < 0.0D ? 0.0D : current;
        return Mth.lerp(ANIMATION_SPEED, start, Mth.clamp(target, 0.0D, 1.0D));
    }

    private record ScrollKey(UUID playerId, ResourceLocation dimension, BlockPos pos) {}

    private record ScrollSnapshot(double leftScrollPixels, double metricScrollPixels, double matrixScrollPixels) {}
}
