package cn.dancingsnow.neoecoae.platform.mixin;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.spongepowered.asm.mixin.injection.struct.RedirectInjectionInfo;
import org.spongepowered.asm.mixin.transformer.MixinTargetContext;

@InjectionInfo.AnnotationType(RouteGtoCpuLogic.class)
@InjectionInfo.HandlerPrefix("redirect")
public final class GtoCpuLogicInjectionInfo extends RedirectInjectionInfo {
    public GtoCpuLogicInjectionInfo(MixinTargetContext mixin, MethodNode method, AnnotationNode annotation) {
        super(mixin, method, annotation);
    }

    @Override protected void parseSelectors() {
        selectors.clear();
        for (var method : classNode.methods) {
            if (GtoCpuLogicSelector.selects(method)) selectors.add(new MemberInfo(method.name, null, method.desc));
        }
    }
}
