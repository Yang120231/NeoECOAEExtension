package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import cn.dancingsnow.neoecoae.client.gui.widget.GtoPatternUploadButton;
import com.gtolib.api.ae2.me2in1.Me2in1Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = Me2in1Screen.class, remap = false)
public abstract class GtoMe2in1ScreenMixin {
    @Shadow
    public abstract Map<String, AbstractWidget> getSubWidgets();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void neoecoae$addUploadButton(CallbackInfo ci) {
        var screen = (AEBaseScreen<?>) (Object) this;
        var widgets = screen.getWidgets();
        ICompositeWidget encodingPanel = widgets.getCompositeWidgets().get("PatternEncodingModule_encodePanel");
        // Registered after the encoding panel so its current bounds are available each frame.
        widgets.add("neoecoaeUploadPattern", (ICompositeWidget) new GtoPatternUploadButton(
                screen, encodingPanel, getSubWidgets().get("modeTabButton_CRAFTING")));
    }
}
