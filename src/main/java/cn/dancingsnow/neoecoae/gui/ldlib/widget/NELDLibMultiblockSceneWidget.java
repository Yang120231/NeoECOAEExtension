package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScene;
import cn.dancingsnow.neoecoae.client.multiblock.preview.NEMultiblockSceneRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibMultiblockSceneAdapter;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;

public class NELDLibMultiblockSceneWidget extends Widget {
    private final Supplier<MultiblockPreviewScene> sceneSupplier;
    private final NEMultiblockSceneRenderer renderer = new NEMultiblockSceneRenderer();

    private boolean draggingScene;

    public NELDLibMultiblockSceneWidget(
            int x, int y, int width, int height, Supplier<MultiblockPreviewScene> sceneSupplier) {
        super(x, y, width, height);
        this.sceneSupplier = sceneSupplier;
        renderer.setYaw(NELDLibMultiblockSceneAdapter.DEFAULT_YAW);
        renderer.setPitch(NELDLibMultiblockSceneAdapter.DEFAULT_PITCH);
        renderer.setZoom(NELDLibMultiblockSceneAdapter.DEFAULT_ZOOM);
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        MultiblockPreviewScene scene = sceneSupplier.get();
        renderer.render(
                graphics,
                scene,
                scene == null ? java.util.List.of() : scene.orderedPositions(),
                getPositionX(),
                getPositionY(),
                getSizeWidth(),
                getSizeHeight(),
                partialTicks,
                true);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (wheelDelta != 0.0D && isMouseOverElement(mouseX, mouseY)) {
            renderer.adjustZoom(wheelDelta);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOverElement(mouseX, mouseY)) {
            draggingScene = true;
            return true;
        }
        draggingScene = false;
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScene) {
            renderer.rotateFromMouseDrag(dragX, dragY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScene) {
            draggingScene = false;
            return true;
        }
        return false;
    }
}
