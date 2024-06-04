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
	 * @return the {@link Class} to target for patching
	 */
	Class<?> value();

	/**
	 * This contains the inner class name(s) to append, separated by a $ symbol, to the already
	 * acquired fully-qualified name.
	 * If a number is provided instead of a valid name, the class will be treated as an
	 * anonymous class, and will therefore be skipped in verification.
	 * @return the name or path of the inner class that contain the target, defaults to array containing
	 *         a single empty string (not an inner class)
	 * @since 0.5.0
	 */
	String[] inner() default {};
}
