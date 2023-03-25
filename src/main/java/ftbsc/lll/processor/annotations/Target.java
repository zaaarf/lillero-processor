package ftbsc.lll.processor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a method as the target method.
 * The method annotated with this, called "stub" within the documentation, should have the
 * same name and parameters as the method it's supposed to represent.
 * It will be discarded unless the containing class is annotated with {@link Patch}
 * and another method within the class is annotated with {@link Injector}.
 * @see Patch
 * @see Injector
 */
@Retention(RetentionPolicy.CLASS)
@Repeatable(MultipleTargets.class)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface Target {

	/**
	 * Indicates which of the methods annotated with {@link Find} or {@link Injector}
	 * is targeting this stub.
	 * @return the name of the element this is supposed to apply to
	 * @since 0.5.0
	 */
	String of() default "";

	/**
	 * When set to false, tells the processor to first try to match a single method by name,
	 * and to only check parameters if further clarification is needed.
	 * While non-strict mode is more computationally efficient, it's ultimately not
	 * relevant, as it only matters at compile time. Do not set this to false unless
	 * you know what you're doing.
	 * @return whether strict mode is to be used
	 * @since 0.3.0
	 */
	boolean strict() default true;
}
