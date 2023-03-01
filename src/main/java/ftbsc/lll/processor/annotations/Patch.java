package ftbsc.lll.processor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks the class as containing an injector for a user-specified {@link Class}.
 * It will be discarded unless {@link Target} and {@link Injector} are properly
 * placed within the annotated class.
 * @see Target
 * @see Injector
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.TYPE)
public @interface Patch {
	/**
	 * @return the Minecraft {@link Class} to target for patching
	 */
	Class<?> value();

	/**
	 * @return the patching reason, for logging, defaults to "No reason specified."
	 */
	String reason() default "No reason specified.";
}
