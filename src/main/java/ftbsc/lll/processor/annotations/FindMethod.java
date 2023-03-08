package ftbsc.lll.processor.annotations;

import ftbsc.lll.proxies.MethodProxy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Overrides the marked method in the Injector, having the
 * implementation return a built {@link MethodProxy} with
 * the specified parameters.
 * @implNote if name is omitted, the name of the annotated
 *           method is used.
 * @since 0.2.0
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface FindMethod {
	Class<?> parent() default Object.class;
	String name() default "";
	Class<?>[] params() default {};
}
