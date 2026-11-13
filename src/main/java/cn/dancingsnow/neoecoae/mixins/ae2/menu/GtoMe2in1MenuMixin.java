package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.menu.AEBaseMenu;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import cn.dancingsnow.neoecoae.compat.gto.ECOGtoPatternTerminal;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.AEBaseMenuClientActionAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GTO's terminal extends PatternAccessTermMenu and delegates encoding to a MEStorageMenu module. */
@Mixin(value = AEBaseMenu.class, remap = false)
public abstract class GtoMe2in1MenuMixin implements PatternEncodingTermMenuExtension {
    @Unique
    private static final String NEOECOAE_UPLOAD_PATTERN = "neoecoae:uploadPattern";
    @Unique
    private boolean neoecoae$uploadRegistered;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void neoecoae$registerUpload(CallbackInfo ci) {
        var menu = (AEBaseMenu) (Object) this;
        if (neoecoae$uploadRegistered || !ECOGtoPatternTerminal.isMenu(menu)) return;
        neoecoae$uploadRegistered = true;
        ((AEBaseMenuClientActionAccessor) this).neoecoae$registerClientAction(
            NEOECOAE_UPLOAD_PATTERN, this::neoecoae$uploadPattern);
    }

    @Override
    public void neoecoae$uploadPattern() {
        var menu = (AEBaseMenu) (Object) this;
        if (!ECOGtoPatternTerminal.isMenu(menu)) return;
        if (menu.isClientSide()) {
            ((AEBaseMenuClientActionAccessor) this).neoecoae$sendClientAction(NEOECOAE_UPLOAD_PATTERN);
            return;
        }
        ECOGtoPatternTerminal.upload(menu);
    }
}
