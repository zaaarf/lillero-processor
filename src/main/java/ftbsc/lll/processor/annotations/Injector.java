package ftbsc.lll.processor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a method as the injector method for purposes of generation.
 * The method itself should take in a ClassNode and MethodNode (from the ObjectWeb ASM library)
 * as parameters. It will be discarded otherwise.
 * It will also be discarded unless the containing class is annotated with {@link Patch}
 * and another method within the class is annotated with {@link Target}.
 * This annotation may be added multiple times, in order to target multiple methods.
 * @see Patch
 * @see Target
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface Injector {
	/**
	 * @return the patching reason, for logging, defaults to "No reason specified."
	 * @since 0.5.0
	 */
	String reason() default "No reason specified.";
}
