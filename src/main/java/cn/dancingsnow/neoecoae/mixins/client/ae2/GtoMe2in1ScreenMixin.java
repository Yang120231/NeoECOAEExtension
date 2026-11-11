package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.AEBaseScreen;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import cn.dancingsnow.neoecoae.gui.widget.UploadButton;
import com.gtolib.api.ae2.me2in1.Me2in1Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Me2in1Screen.class, remap = false)
public abstract class GtoMe2in1ScreenMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void neoecoae$addUploadButton(CallbackInfo ci) {
        // The toolbar handles positioning and resizing alongside GTO's movable panels.
        var screen = (AEBaseScreen<?>) (Object) this;
        screen.addToLeftToolbar(new UploadButton(0, 0,
                button -> ((PatternEncodingTermMenuExtension) screen.getMenu()).neoecoae$uploadPattern()));
    }
}
