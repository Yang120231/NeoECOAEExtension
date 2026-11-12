package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GTO's menu mutates its previous counter during diffing, so only the immutable publication is shared. */
@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class GtoStorageMenuSnapshotMixin {
    @Shadow @Final protected MEStorage storage;
    @org.spongepowered.asm.mixin.Unique
    private final cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalExactAmountSync neoecoae$exactAmountSync =
        new cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalExactAmountSync();

    @WrapOperation(method = "broadcastChanges()V", remap = true, at = @At(value = "INVOKE", remap = false, target =
        "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"))
    private void neoecoae$sampleInventory(MEStorage source, KeyCounter output, Operation<Void> original) {
        var menu = (MEStorageMenu) (Object) this;
        if (menu.getPlayer().getServer() == null || !output.isEmpty()) { original.call(source, output); return; }
        boolean[] sampled = {false};
        // The same listing also feeds the exact-amount side channel for stock beyond AE2's long range.
        KeyCounter inventory = neoecoae$exactAmountSync.collect(menu, source, () -> {
            sampled[0] = true;
            original.call(source, output);
            return output;
        });
        // A cache miss already filled this menu's own counter. On a hit the native bulk copy keeps it independent.
        if (!sampled[0]) output.addAll(inventory);
    }

    @Inject(method = {"handleInteraction", "onSettingChanged", "putCarriedItemIntoNetwork"}, at = @At("HEAD"))
    private void neoecoae$refreshAfterPlayerChange(CallbackInfo callback) {
        if (storage != null) TerminalInventorySnapshot.invalidate(TerminalInventorySnapshot.resolve(storage));
    }
}
