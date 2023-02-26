package ftbsc.lll.processor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Marks a method as the injector method for purposes of generation.
 * The method itself should be {@code public static}, and take in a {@link ClassNode}
 * and a {@link MethodNode} as parameters. It will be discarded otherwise.
 * It will also be discarded unless the containing class is not annotated with {@link Patch}
 * and no other method within the class is annotated with {@link Target}.
 * @see Patch
 * @see Target
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface Injector {}
