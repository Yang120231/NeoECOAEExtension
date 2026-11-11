package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ITerminalHost;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.encoding.EncodingMode;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import cn.dancingsnow.neoecoae.crafting.PatternTerminalUpload;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternEncodingTermMenu.class, remap = false)
public class PatternEncodingTermMenuMixin extends MEStorageMenu implements PatternEncodingTermMenuExtension {
    @Shadow
    @Final
    private RestrictedInputSlot encodedPatternSlot;
    @Shadow
    private EncodingMode currentMode;
    @Unique
    private final String ACTION_UPLOAD_PATTERN = "neoecoae:uploadPattern";

    public PatternEncodingTermMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(
        method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V",
        at = @At("RETURN")
    )
    void onRegisterClientActions(
        MenuType<?> menuType,
        int id,
        Inventory ip,
        IPatternTerminalMenuHost host,
        boolean bindInventory,
        CallbackInfo ci
    ) {
        registerClientAction(ACTION_UPLOAD_PATTERN, this::neoecoae$uploadPattern);
    }

    @Override
    public void neoecoae$uploadPattern() {
        if (isClientSide()) {
            sendClientAction(ACTION_UPLOAD_PATTERN);
            return;
        }
        IGridNode node = getNetworkNode();
        if (node == null) return;
        if (!canInteractWithGrid()) return;
        if (currentMode == EncodingMode.PROCESSING) return;
        IGrid grid = node.getGrid();
        if (grid == null) return;
        PatternTerminalUpload.upload(grid, getPlayer(), encodedPatternSlot);
    }
}
