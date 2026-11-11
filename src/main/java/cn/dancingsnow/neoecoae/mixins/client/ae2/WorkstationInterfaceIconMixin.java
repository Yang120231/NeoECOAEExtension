package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import cn.dancingsnow.neoecoae.client.gui.workstation.WorkstationInterfaceArtwork;
import gto_ae.hooks.gui.IIcon;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Icon.class, remap = false)
public abstract class WorkstationInterfaceIconMixin implements IIcon {
    @Override
    public Blitter getBlitter() {
        if (WorkstationInterfaceArtwork.active()) {
            return WorkstationInterfaceArtwork.icon((Icon) (Object) this);
        }
        // Mixin rebinds interface-super calls to Icon, producing an invalid InterfaceMethodref.
        IIcon icon = (IIcon) (Object) this;
        return Blitter.texture(icon.getIconTexture(), icon.getIconTextureWidth(), icon.getIconTextureHeight())
            .src(icon.getIconX(), icon.getIconY(), icon.getIconWidth(), icon.getIconHeight());
    }
}
