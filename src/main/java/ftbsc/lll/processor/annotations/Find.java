package ftbsc.lll.processor.annotations;

import ftbsc.lll.proxies.FieldProxy;
import ftbsc.lll.proxies.MethodProxy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Overrides the marked method in the Injector, having the
 * implementation return a built {@link MethodProxy} or
 * {@link FieldProxy} with the specified characteristics.
 * @since 0.4.0
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface Find {
	/**
	 * @return the {@link Class} object containing the target, or the
	 * {@link Object} class if not specified (the {@link Class} from
	 * {@link Patch#value()} is instead used)
	 */
	Class<?> parent() default Object.class;

	/**
	 * @return the name of the inner class that contains the target,
	 * defaults to empty string (not an inner class)
	 * @since 0.4.0
	 */
	String parentInnerClass() default "";

	/**
	 * @return the anonymous class counter (1 for the first, 2 for
	 * the second, 3 for the third...) for the class that contains
	 * the target, defaults to 0 (not an anonymous class)
	 * @since 0.4.0
	 */
	int parentAnonymousClassCounter() default 0;

	/**
	 * The name of the class member to find. If omitted, the name of the
	 * annotated method will be used.
	 * @return the name of the target, will default to the empty string
	 * (the name of the annotated method will instead be used)
	 */
	String name() default "";

	/**
	 * Only use if the target is a method.
	 * @return a list of the parameters of the method, will default to empty
	 * array (in that case, an attempt will be made to match a method without
	 * args first)
	 */
	Class<?>[] params() default {};
}
