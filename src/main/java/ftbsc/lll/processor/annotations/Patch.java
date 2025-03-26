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
	 * Contains the fully-qualified name of the target. This is intended for cases where
	 * the class may not be available at compile time and always overrides {@link #value()},
	 * regardless of what its contents are.
	 * If used, {@link #inner()} is still respected.
	 * Inner classes may be specified here, but the '$' separator is to be used instead
	 * of the '.': an example would be <code>some.package.SomeClass$SomeInnerClass</code>.
	 * A verification of the provided FQN is still attempted: if the class is found in the
	 * compile-time classpath, it is treated normally as if specified by {@link #value()};
	 * otherwise, it is treated the same as an anonymous class, and any of its children will
	 * not be verified either.
	 * @return the fully-qualified name of the target, which overrides {@link #value()}
	 * @since 0.8.8
	 */
	String fqn() default "";

	/**
	 * This contains the inner class name(s) to append, separated by a $ symbol, to the already
	 * acquired fully-qualified name.
	 * If a number is provided instead of a valid name, the class will be treated as an
	 * anonymous class, and will therefore be skipped in verification.
	 * @return the name or path of the inner class that contain the target
	 * @since 0.5.0
	 */
	String[] inner() default {};
}
