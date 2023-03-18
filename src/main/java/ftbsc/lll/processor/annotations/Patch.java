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

	/**
 	 * @return the name of the inner class that should be targeted,
	 * defaults to empty string (not an inner class)
	 * @since 0.4.0
	 */
	String innerClass() default "";

	/**
	 * @return the anonymous class counter (1 for the first, 2 for
	 * the second, 3 for the third...) for the class that should be
	 * targeted, defaults to 0 (not an anonymous class)
	 * @since 0.4.0
	 */
	int anonymousClassCounter() default 0;
}
