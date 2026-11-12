package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellSettings;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Context actions for an installed cell: removal, and the cell-local priority where it applies. */
final class StorageCellActionsPopup extends UIElement {
    private static final int MIN_WIDTH = 126;
    private static final int VERTICAL_PADDING = 4;
    private static final int ROW_HEIGHT = 20;
    private static final int PRIORITY_COLOR = 0xFF006600;

    private enum Action {
        EJECT("gui.neoecoae.storage.cell.action.eject"),
        PRIORITY("gui.neoecoae.storage.bulk_cell.action.priority");

        private final String key;
        Action(String key) { this.key = key; }
        Component label() { return Component.translatable(key); }
    }

    @FunctionalInterface
    interface Eject {
        void eject(BlockPos position, UUID installation, boolean toInventory);
    }

    private final Eject eject;
    private final BiConsumer<BlockPos, UUID> priority;
    private BlockPos position;
    private UUID installation;
    private Supplier<StorageHostUI.CellEntry> selected;
    private List<Action> actions = List.of();
    private int width = MIN_WIDTH;
    private int height;
    private int hovered = -1;
    private int pressed = -1;

    StorageCellActionsPopup(Eject eject, BiConsumer<BlockPos, UUID> priority) {
        this.eject = eject;
        this.priority = priority;
        setId("eco-storage-cell-actions");
        setDisplay(false);
        style(style -> style.zIndex(400));
        setOverflowVisible(true);
        // Stop tooltip lookup here instead of falling through to the disk underneath the menu.
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            int row = row(event.x, event.y);
            event.hoverTooltips = row >= 0 && row < actions.size() && actions.get(row) == Action.EJECT
                ? HoverTooltips.empty().append(Component.translatable("gui.neoecoae.storage.cell.action.eject.hint"))
                : HoverTooltips.empty();
        });
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            pressed = event.button == 0 ? row(event.x, event.y) : -1;
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.MOUSE_UP, event -> {
            int row = row(event.x, event.y);
            if (event.button == 0 && row == pressed) activate(row);
            pressed = -1;
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.MOUSE_MOVE, event -> hovered = row(event.x, event.y));
        addEventListener(UIEvents.MOUSE_LEAVE, event -> hovered = -1);
        addEventListener(UIEvents.MOUSE_WHEEL, event -> event.stopImmediatePropagation());
    }

    void open(StorageHostUI.CellEntry cell, Supplier<StorageHostUI.CellEntry> selected,
        float x, float y, int parentWidth, int parentHeight) {
        this.position = cell.position();
        this.installation = cell.interactionId();
        this.selected = selected;
        List<Action> available = new ArrayList<>();
        available.add(Action.EJECT);
        // Members, migrating sources and decorative matrices are not mounted, so they have no priority.
        if (ECOStorageCellSettings.canConfigure(cell.stack()) && !cell.decorative()) available.add(Action.PRIORITY);
        actions = List.copyOf(available);
        var font = Minecraft.getInstance().font;
        width = MIN_WIDTH;
        for (var action : actions) {
            int numberWidth = action == Action.PRIORITY ? font.width(String.valueOf(Integer.MIN_VALUE)) + 12 : 0;
            width = Math.max(width, font.width(action.label()) + numberWidth + 16);
        }
        height = VERTICAL_PADDING * 2 + ROW_HEIGHT * actions.size();
        hovered = pressed = -1;
        int left = Math.max(0, Math.min(Math.max(0, parentWidth - width), Math.round(x)));
        int top = Math.max(0, Math.min(Math.max(0, parentHeight - height), Math.round(y)));
        layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(left).top(top)
            .width(width).height(height));
        setDisplay(true);
    }

    void close() {
        setDisplay(false);
        position = null;
        installation = null;
        selected = null;
        hovered = pressed = -1;
    }

    boolean isOpen() {
        return position != null;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (isOpen() && key == 256) {
            close();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private int row(double x, double y) {
        if (!isOpen() || x < getPositionX() || x >= getPositionX() + width
            || y < getPositionY() || y >= getPositionY() + height) return -1;
        int localY = (int) (y - getPositionY());
        if (localY < VERTICAL_PADDING || localY >= height - VERTICAL_PADDING) return -1;
        int row = (localY - VERTICAL_PADDING) / ROW_HEIGHT;
        return row < actions.size() ? row : -1;
    }

    private void activate(int row) {
        if (position == null || row < 0 || row >= actions.size() || !selectionValid()) return;
        BlockPos target = position;
        UUID expected = installation;
        Action action = actions.get(row);
        close();
        switch (action) {
            case EJECT -> eject.eject(target, expected, UIElement.isShiftDown());
            case PRIORITY -> priority.accept(target, expected);
        }
    }

    private boolean selectionValid() {
        if (selected == null || installation == null) return false;
        var cell = selected.get();
        return installation.equals(cell.interactionId()) && position.equals(cell.position())
            && !cell.stack().isEmpty();
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        if (!isOpen()) return;
        if (!selectionValid()) {
            close();
            return;
        }
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        context.graphics.flush();
        context.drawTexture(NETextures.AE2_CONTEXT_BACKGROUND, x, y, width, height);
        // Flush the background before drawing the hover overlay and labels.
        context.graphics.flush();
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            int top = y + VERTICAL_PADDING + i * ROW_HEIGHT;
            if (i == hovered) context.graphics.fill(x + 4, top + 2, x + width - 4, top + ROW_HEIGHT - 2, 0x807B81B8);
            int color = i == hovered ? 0xFFFFFF : 0x413F54;
            int textY = top + (ROW_HEIGHT - font.lineHeight) / 2;
            context.graphics.drawString(font, action.label().getString(), x + 8, textY, 0xFF000000 | color, false);
            if (action == Action.PRIORITY) {
                String value = String.valueOf(ECOStorageCellSettings.priority(selected.get().stack()));
                context.graphics.drawString(font, value, x + width - 8 - font.width(value), textY, PRIORITY_COLOR, false);
            }
        }
        context.graphics.flush();
    }
}
