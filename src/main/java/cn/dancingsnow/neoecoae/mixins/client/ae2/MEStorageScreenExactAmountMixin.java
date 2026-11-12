package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.api.config.SortOrder;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.menu.me.common.MEStorageMenu;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountClientCache;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountDisplay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gto_ae.hooks.gui.menu.IRepoSlot;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GTO moved slot and tooltip drawing into IRepoSlot; scope the exact amount around those calls. */
@Mixin(value = MEStorageScreen.class, remap = false)
public abstract class MEStorageScreenExactAmountMixin {
    @Shadow @Final protected Repo repo;
    @Unique private long neoecoae$exactAmountRevision = -1;

    @Inject(method = "containerTick()V", remap = true, at = @At("TAIL"))
    private void neoecoae$refreshExactAmountOrder(CallbackInfo ci) {
        long revision = ExactAmountClientCache.revision();
        if (neoecoae$exactAmountRevision != revision) {
            neoecoae$exactAmountRevision = revision;
            var self = (MEStorageScreen<?>) (Object) this;
            if (self.getSortBy() == SortOrder.AMOUNT) {
                // updateView preserves AE's paused (Shift-held) and pinned-row behavior.
                repo.updateView();
            }
        }
    }

    @Inject(method = "removed()V", remap = true, at = @At("HEAD"))
    private void neoecoae$clearExactAmounts(CallbackInfo ci) {
        var self = (MEStorageScreen<?>) (Object) this;
        ExactAmountClientCache.clear(self.getMenu().containerId);
    }

    @WrapOperation(method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V",
        remap = true, at = @At(remap = false, value = "INVOKE", target =
        "Lgto_ae/hooks/gui/menu/IRepoSlot;renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lappeng/client/gui/me/common/Repo;ZZ)V"))
    private void neoecoae$renderExactSlotAmount(IRepoSlot slot, GuiGraphics graphics, Repo slotRepo,
            boolean largeFonts, boolean viewOnlyCraftable, Operation<Void> original) {
        var self = (MEStorageScreen<?>) (Object) this;
        boolean exact = ExactAmountDisplay.begin(self.getMenu().containerId, slot.getEntry());
        try {
            original.call(slot, graphics, slotRepo, largeFonts, viewOnlyCraftable);
        } finally {
            if (exact) ExactAmountDisplay.end();
        }
    }

    @WrapOperation(method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V", remap = true,
        at = @At(remap = false, value = "INVOKE", target =
        "Lgto_ae/hooks/gui/menu/IRepoSlot;renderTooltip(Lappeng/menu/me/common/MEStorageMenu;Lnet/minecraft/client/gui/GuiGraphics;IILgto_ae/hooks/gui/menu/IRepoSlot$TooltipHandler;Lgto_ae/hooks/gui/menu/IRepoSlot$TooltipHandler;Z)V"))
    private void neoecoae$renderExactTooltip(IRepoSlot slot, MEStorageMenu menu, GuiGraphics graphics, int x, int y,
            IRepoSlot.TooltipHandler plain, IRepoSlot.TooltipHandler keyed, boolean viewOnlyCraftable,
            Operation<Void> original) {
        boolean exact = ExactAmountDisplay.begin(menu.containerId, slot.getEntry());
        try {
            original.call(slot, menu, graphics, x, y, plain, keyed, viewOnlyCraftable);
        } finally {
            if (exact) ExactAmountDisplay.end();
        }
    }
}
