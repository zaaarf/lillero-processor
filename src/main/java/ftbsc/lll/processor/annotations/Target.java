package ftbsc.lll.processor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a method as the target method.
 * The method itself should have the same name, return type and parameters as the desired
 * Minecraft method.
 * It will also be discarded unless the containing class is not annotated with {@link Patch}
 * and no other method within the class is annotated with {@link Injector}.
 * @see Patch
 * @see Injector
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface Target {}
