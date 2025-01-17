package ftbsc.lll.processor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Allows specifying what class a certain method is overriding.
 * The generated patch will use the obfuscated name of the method defined by this,
 * and the descriptor of the target. For the purposes of using obfuscated name, you
 * should use this annotation to define the signature of the top-level method.
 * By default, this will be used for any method annotated with {@link Target} with
 * the same name present in the same class.
 * This annotation will be ignored if {@link Target#lookForParent()} is set to false
 * in the matching method.
 * @since 0.8.0
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface Overridden {
	/**
	 * Forces matching against a specific name. This does not make any
	 * sense to use, unless it's to resolve ambiguities by setting
	 * {@link Target#methodName()}.
	 * This will always match against the actual method name, and ignore
	 * the one specified by {@link Target#methodName()}.
	 * @return the name to match this against
	 */
	String by() default "";

	/**
	 * This defines the parent of the method this is annotating.
	 * @return the {@link Class} referencing the parent of the method this is annotating
	 */
	Class<?> parent();

	/**
	 * This defines, if present, the private inner class where the target is contained.
	 * @return the name of the inner class that contains the target, defaults to empty
	 *         string (not an inner class)
	 * @see Patch#inner() for details
	 */
	String[] parentInner() default {};

	/**
	 * Whether to match in strict mode.
	 * @return whether the matching should be in strict mode
	 * @see Target#strict() for details
	 */
	boolean strict() default true;
}
