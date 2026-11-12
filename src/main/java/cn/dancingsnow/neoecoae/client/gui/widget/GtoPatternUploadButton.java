package cn.dancingsnow.neoecoae.client.gui.widget;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import cn.dancingsnow.neoecoae.gui.widget.UploadButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;

import java.util.function.Consumer;

public final class GtoPatternUploadButton extends UploadButton implements ICompositeWidget {
    private final AEBaseScreen<?> screen;
    private final ICompositeWidget encodingPanel;
    private final AbstractWidget modeTab;

    public GtoPatternUploadButton(AEBaseScreen<?> screen, ICompositeWidget encodingPanel, AbstractWidget modeTab) {
        super(0, 0, button -> ((PatternEncodingTermMenuExtension) screen.getMenu()).neoecoae$uploadPattern());
        this.screen = screen;
        this.encodingPanel = encodingPanel;
        this.modeTab = modeTab;
    }

    @Override
    public void populateScreen(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> parent) {
        addWidget.accept(this);
    }

    @Override
    public void setPosition(Point position) {
        // Position follows the encoding panel instead of the screen style.
    }

    @Override
    public void setSize(int width, int height) {
        // Keep the upload tab's native size.
    }

    @Override
    public void updateBeforeRender() {
        var bounds = encodingPanel.getBounds();
        setX(screen.getGuiLeft() + bounds.getX() - getWidth());
        setY(screen.getGuiTop() + bounds.getY());
        visible = modeTab.visible;
        active = modeTab.active;
    }

    @Override
    public Rect2i getBounds() {
        var bounds = encodingPanel.getBounds();
        return new Rect2i(bounds.getX() - getWidth(), bounds.getY(), getWidth(), getHeight());
    }
}
