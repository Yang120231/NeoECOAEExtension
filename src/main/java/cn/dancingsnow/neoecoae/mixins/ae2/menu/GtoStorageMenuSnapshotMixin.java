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

    @WrapOperation(method = "sendInventoryUpdate", at = @At(value = "INVOKE", target =
        "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"))
    private void neoecoae$sampleInventory(MEStorage source, KeyCounter output, Operation<Void> original) {
        var server = ((MEStorageMenu) (Object) this).getPlayer().getServer();
        if (server == null || !output.isEmpty()) { original.call(source, output); return; }
        boolean[] sampled = {false};
        var snapshot = TerminalInventorySnapshot.sample(TerminalInventorySnapshot.resolve(source),
            server.getTickCount(), false, () -> {
                sampled[0] = true;
                original.call(source, output);
                return output;
            });
        // A cache miss already filled this menu's own counter. On a hit the native bulk copy keeps it independent.
        if (!sampled[0]) output.addAll(snapshot.inventory());
    }

    @Inject(method = {"notifyStorageChanged", "setViewFrozen"}, at = @At("HEAD"))
    private void neoecoae$refreshAfterPlayerChange(CallbackInfo callback) {
        if (storage != null) TerminalInventorySnapshot.invalidate(TerminalInventorySnapshot.resolve(storage));
    }
}
