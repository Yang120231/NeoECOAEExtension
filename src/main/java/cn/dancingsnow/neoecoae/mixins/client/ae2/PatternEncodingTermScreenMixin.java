package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.items.PatternEncodingTermMenu;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import cn.dancingsnow.neoecoae.gui.widget.UploadButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternEncodingTermScreen.class, remap = false)
public class PatternEncodingTermScreenMixin<C extends PatternEncodingTermMenu> extends MEStorageScreen<C> {
    @Unique
    private UploadButton neoecoae$uploadButton;

    public PatternEncodingTermScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void neoecoae$addUploadButton(CallbackInfo ci) {
        neoecoae$uploadButton = new UploadButton(0, 0,
            b -> ((PatternEncodingTermMenuExtension) getMenu()).neoecoae$uploadPattern());
        widgets.add("neoecoaeUploadPattern", neoecoae$uploadButton);
    }

    @Override
    public void init() {
        super.init();
        // Follow the mode tabs after AE2 resolves their positions, including on resize.
        var lastModeTab = widgets.getWidgets().get("modeTabButton3");
        neoecoae$uploadButton.setX(lastModeTab.getX());
        neoecoae$uploadButton.setY(lastModeTab.getY() + lastModeTab.getHeight());
    }
}
