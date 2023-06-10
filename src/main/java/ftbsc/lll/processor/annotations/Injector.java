package ftbsc.lll.processor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a method as the injector method for purposes of generation.
 * The method itself should take in as parameters a ClassNode and a MethodNode (from the ObjectWeb
 * ASM library), or only a MethodNode. The annotation will be ignored otherwise.
 * It will also be discarded unless the containing class is annotated with {@link Patch}
 * and at least another method within the class is annotated with {@link Target}.
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
