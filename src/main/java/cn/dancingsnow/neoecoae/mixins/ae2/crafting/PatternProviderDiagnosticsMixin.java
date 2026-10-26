package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOPatternPushDiagnostics;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider;
import cn.dancingsnow.neoecoae.config.NEConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes existing calls only: no extra target probes, insertion, or changes to AE2's return value. */
@Mixin(value = PatternProviderLogic.class, remap = false, priority = 500)
public abstract class PatternProviderDiagnosticsMixin implements ECOPatternPushDiagnostics, ECOProcessingTargetProvider {
    @cn.dancingsnow.neoecoae.platform.mixin.ObserveGtoPattern(helper = "@providerCallback",
        at = @At("HEAD"), cancellable = true)
    private void neoecoae$routeSpecializedProvider(IPatternDetails pattern,
            @org.spongepowered.asm.mixin.injection.Coerce Object holder, java.util.function.Supplier<?> accepted,
            CallbackInfoReturnable<Object> ci) {
        Object self = this;
        if (self instanceof cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider provider) {
            ci.setReturnValue(cn.dancingsnow.neoecoae.compat.gto.ECOGtoPatternPush
                .dispatchSpecialized(provider, pattern, holder, accepted));
        }
    }

    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow @Final private List<GenericStack> sendList;
    @Shadow private Direction sendDirection;
    @Shadow @Final private IManagedGridNode mainNode;
    @Shadow @Final private List<IPatternDetails> patterns;
    @Shadow public abstract LockCraftingMode getCraftingLockedReason();

    @Unique private final Set<Reason> neoecoae$rejectReasons = EnumSet.noneOf(Reason.class);
    @Unique private boolean neoecoae$observingPush;
    @Unique private boolean neoecoae$foundTarget;
    @Unique private boolean neoecoae$capturingTarget;
    @Unique private Direction neoecoae$targetSide;
    @Unique private Target neoecoae$processingTarget;

    @Override
    public Target neoecoae$getProcessingTarget() { return neoecoae$processingTarget; }

    @Override
    public void neoecoae$clearPushDiagnostics() {
        neoecoae$rejectReasons.clear();
        neoecoae$foundTarget = false;
    }

    @Override
    public Snapshot neoecoae$getPushDiagnostics() {
        var reasons = EnumSet.noneOf(Reason.class);
        reasons.addAll(neoecoae$rejectReasons);
        if (!sendList.isEmpty()) reasons.add(Reason.SEND_LIST_BUSY);
        var be = host.getBlockEntity();
        var level = be.getLevel();
        String location = (level == null ? "unknown" : level.dimension().location().toString())
            + "@" + be.getBlockPos().toShortString();
        return new Snapshot(location, reasons, sendList.size(),
            sendDirection == null ? "none" : sendDirection.getName());
    }

    @WrapMethod(method = "pushPattern")
    private boolean neoecoae$observePush(IPatternDetails pattern, KeyCounter[] inputs,
            Operation<Boolean> original) {
        neoecoae$processingTarget = null;
        neoecoae$targetSide = null;
        neoecoae$capturingTarget = true;
        neoecoae$observingPush = NEConfig.ecoDispatchWatchdogDebug;
        if (neoecoae$observingPush) {
            neoecoae$clearPushDiagnostics();
            if (!mainNode.isActive()) neoecoae$rejectReasons.add(Reason.NODE_INACTIVE);
            if (!patterns.contains(pattern)) neoecoae$rejectReasons.add(Reason.PATTERN_MISSING);
            if (!sendList.isEmpty()) neoecoae$rejectReasons.add(Reason.SEND_LIST_BUSY);
            if (getCraftingLockedReason() != LockCraftingMode.NONE) neoecoae$rejectReasons.add(Reason.CRAFTING_LOCKED);
        }
        try {
            boolean accepted = cn.dancingsnow.neoecoae.compat.gto.ECOGtoPatternPush.push(
                    (PatternProviderLogic) (Object) this, pattern, inputs);
            var be = host.getBlockEntity();
            if (accepted && neoecoae$targetSide != null && be.getLevel() != null) {
                neoecoae$processingTarget = new Target(be.getLevel().dimension().location(),
                    be.getBlockPos().relative(neoecoae$targetSide));
            }
            if (neoecoae$observingPush && accepted) neoecoae$rejectReasons.clear();
            else if (neoecoae$observingPush && neoecoae$rejectReasons.isEmpty()) {
                neoecoae$rejectReasons.add(neoecoae$foundTarget ? Reason.UNKNOWN : Reason.NO_EXTERNAL_TARGET);
            }
            return accepted;
        } finally {
            neoecoae$observingPush = false;
            neoecoae$capturingTarget = false;
        }
    }

    // Observe GTO's merged helpers instead of calls removed from the vanilla pushPattern body.
    @cn.dancingsnow.neoecoae.platform.mixin.ObserveGtoPattern(helper = "gtolib$findTarget", at = @At("RETURN"))
    private void neoecoae$observeGtoTarget(Direction side, CallbackInfoReturnable<PatternProviderTarget> result) {
        if (result.getReturnValue() != null) {
            if (neoecoae$observingPush) neoecoae$foundTarget = true;
            if (neoecoae$capturingTarget) neoecoae$targetSide = side;
        }
    }

    @cn.dancingsnow.neoecoae.platform.mixin.ObserveGtoPattern(helper = "gtolib$pushCraftingMachine", at = @At("HEAD"))
    private void neoecoae$observeGtoMachine(CallbackInfoReturnable<Object> result,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) Direction side) {
        if (neoecoae$observingPush) neoecoae$foundTarget = true;
        if (neoecoae$capturingTarget) neoecoae$targetSide = side.getOpposite();
    }

    @Inject(method = "adapterAcceptsAll", at = @At("RETURN"))
    private void neoecoae$observeInputRejection(PatternProviderTarget target, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> result) {
        if (neoecoae$observingPush && !result.getReturnValue()) {
            neoecoae$rejectReasons.add(Reason.TARGET_REJECTED_INPUT);
        }
    }
}
