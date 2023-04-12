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
	 * This is the inner class name to append after a $ symbol to the already acquired
	 * fully-qualified name. If it's a number instead of a valid name, the class will be
	 * treated as an anonymous class, and will therefore be automatically unverified.
	 * @return the name of the inner class that contains the target,
	 * defaults to empty string (not an inner class)
	 * @since 0.5.0
	 */
	String innerClass() default "";
}
