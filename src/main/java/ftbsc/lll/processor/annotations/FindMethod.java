package ftbsc.lll.processor.annotations;

import ftbsc.lll.proxies.MethodProxy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Overrides the marked method in the Injector, having the
 * implementation return a built {@link MethodProxy} with
 * the specified parameters.
 * @since 0.2.0
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface FindMethod {
	/**
	 * @return the {@link Class} object containing the desired method,
	 * or the {@link Object} class if not specified (the {@link Class}
	 * from {@link Patch#value()} is instead used)
	 */
	Class<?> parent() default Object.class;

	/**
	 * The name of the method to find. If omitted, the name of the annotated
	 * method will be used.
	 * @return the name of the method, will default to the empty string
	 * (the name of the annotated method will instead be used)
	 */
	String name() default "";

	/**
	 * @return a list of the parameters of the method, will default to empty
	 * array (in that case, an attempt will be made to match a method without
	 * args first)
	 */
	Class<?>[] params() default {};
}
