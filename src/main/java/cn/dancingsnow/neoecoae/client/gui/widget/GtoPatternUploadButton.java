package cn.dancingsnow.neoecoae.client.gui.widget;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import cn.dancingsnow.neoecoae.compat.gto.ECOGtoPatternTerminal;
import cn.dancingsnow.neoecoae.gui.widget.UploadButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.Map;
import java.util.function.Consumer;

public final class GtoPatternUploadButton extends UploadButton implements ICompositeWidget {
    private final AEBaseScreen<?> screen;
    private final ICompositeWidget encodingPanel;

    public GtoPatternUploadButton(AEBaseScreen<?> screen, ICompositeWidget encodingPanel) {
        super(0, 0, button -> ((PatternEncodingTermMenuExtension) screen.getMenu()).neoecoae$uploadPattern());
        this.screen = screen;
        this.encodingPanel = encodingPanel;
    }

    /** The native screen rebuilds its widget container during init, after its constructor. */
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AEBaseScreen<?> screen)
                || !ECOGtoPatternTerminal.isMenu(screen.getMenu())) return;
        var widgets = screen.getWidgets();
        ICompositeWidget encodingPanel = widgets.getCompositeWidgets().entrySet().stream()
            .filter(entry -> entry.getKey().endsWith("_encodePanel")).map(Map.Entry::getValue)
            .reduce((first, second) -> { throw new IllegalStateException("Ambiguous GTO encoding panel"); })
            .orElseThrow(() -> new IllegalStateException("Missing GTO encoding panel"));
        var existing = widgets.getCompositeWidgets().get("neoecoaeUploadPattern");
        var upload = existing instanceof GtoPatternUploadButton old && old.encodingPanel == encodingPanel
            ? old : new GtoPatternUploadButton(screen, encodingPanel);
        widgets.add("neoecoaeUploadPattern", (ICompositeWidget) upload);
        event.removeListener(upload);
        event.addListener(upload);
        upload.updateBeforeRender();
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
        visible = encodingPanel.isVisible() && ECOGtoPatternTerminal.canUpload(screen.getMenu());
        active = visible;
    }

    @Override
    public Rect2i getBounds() {
        var bounds = encodingPanel.getBounds();
        return new Rect2i(bounds.getX() - getWidth(), bounds.getY(), getWidth(), getHeight());
    }
}
