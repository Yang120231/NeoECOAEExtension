package cn.dancingsnow.neoecoae.platform.mixin;

import java.lang.annotation.*;
import org.spongepowered.asm.mixin.injection.At;

/** Required observation of a helper merged by GTO before the injection phase. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ObserveGtoPattern {
    String helper();
    String descriptor() default "";
    At[] at();
    int require() default 1;
    boolean cancellable() default false;
}
