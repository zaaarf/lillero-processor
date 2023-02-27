package ftbsc.lll.processor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a method as the injector method for purposes of generation.
 * The method itself should be {@code public static}, and take in a ClassNode and MethodNode
 * (from the ObjectWeb ASM library) as parameters. It will be discarded otherwise.
 * It will also be discarded unless the containing class is not annotated with {@link Patch}
 * and no other method within the class is annotated with {@link Target}.
 * @see Patch
 * @see Target
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface Injector {}
