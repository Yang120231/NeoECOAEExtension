package cn.dancingsnow.neoecoae.platform.mixin;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.struct.*;
import org.spongepowered.asm.mixin.transformer.MixinTargetContext;
import org.spongepowered.asm.util.Annotations;

/** Mixin 0.8.5's wildcard selector excludes methods merged by other Mixins. Resolve exact descriptors. */
@InjectionInfo.AnnotationType(ObserveGtoPattern.class)
@InjectionInfo.HandlerPrefix("handler")
public final class GtoPatternObservationInjectionInfo extends CallbackInjectionInfo {
    public GtoPatternObservationInjectionInfo(MixinTargetContext mixin, MethodNode method, AnnotationNode annotation) {
        super(mixin, method, annotation);
    }
    @Override protected void parseSelectors() {
        selectors.clear();
        String helper = Annotations.getValue(annotation, "helper");
        String descriptor = Annotations.getValue(annotation, "descriptor", "");
        for (var method : classNode.methods) {
            boolean matches = helper.equals("@providerCallback")
                ? method.desc.startsWith("(Lappeng/api/crafting/IPatternDetails;Lcom/gto/datasynclib/util/holder/ObjHolder;Ljava/util/function/Supplier;)L")
                : method.name.contains(helper);
            if (matches && (descriptor.isEmpty() || method.desc.equals(descriptor))) {
                selectors.add(new MemberInfo(method.name, null, method.desc));
            }
        }
    }
}
