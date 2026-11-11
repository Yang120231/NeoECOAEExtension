package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.menu.AEBaseMenu;
import appeng.api.networking.IGridNode;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import cn.dancingsnow.neoecoae.crafting.PatternTerminalUpload;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.AEBaseMenuClientActionAccessor;
import com.gtolib.api.ae2.me2in1.IExtendedPatternMenuHost;
import com.gtolib.api.ae2.me2in1.ExtendedEncodingMenu;
import com.gtolib.api.ae2.me2in1.Me2in1Menu;
import com.gtolib.api.ae2.me2in1.encoding.ExtendedEncodingMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Me2in1Menu.class, remap = false)
public abstract class GtoMe2in1MenuMixin implements PatternEncodingTermMenuExtension {
    @Unique
    private static final String NEOECOAE_UPLOAD_PATTERN = "neoecoae:uploadPattern";

    @Shadow public abstract IGridNode getNetworkNode();
    @Shadow public abstract ExtendedEncodingMenu getEncoding();

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lcom/gtolib/api/ae2/me2in1/IExtendedPatternMenuHost;Z)V",
            at = @At("RETURN"))
    private void neoecoae$registerUpload(MenuType<?> type, int id, Inventory inventory,
                                        IExtendedPatternMenuHost host, boolean bindInventory, CallbackInfo ci) {
        ((AEBaseMenuClientActionAccessor) this).neoecoae$registerClientAction(
                NEOECOAE_UPLOAD_PATTERN, this::neoecoae$uploadPattern);
    }

    @Override
    public void neoecoae$uploadPattern() {
        var menu = (AEBaseMenu) (Object) this;
        if (menu.isClientSide()) {
            ((AEBaseMenuClientActionAccessor) this).neoecoae$sendClientAction(NEOECOAE_UPLOAD_PATTERN);
            return;
        }
        var node = getNetworkNode();
        if (!menu.isValidMenu() || node == null || !node.isActive()) return;
        var encoding = getEncoding();
        var mode = encoding.getCurrentMode();
        if (mode != ExtendedEncodingMode.CRAFTING && mode != ExtendedEncodingMode.SMITHING_TABLE
                && mode != ExtendedEncodingMode.STONECUTTING) return;
        for (var slot : encoding.getEncodedPatternSlots()) {
            PatternTerminalUpload.upload(node.getGrid(), menu.getPlayer(), slot);
        }
    }
}
