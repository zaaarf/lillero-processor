package ftbsc.lll.processor.annotations;

import ftbsc.lll.proxies.FieldProxy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Overrides the marked method in the Injector, having the
 * implementation return a built {@link FieldProxy}.
 * @implNote if name is omitted, name of the annotated method
 *           is used.
 * @since 0.2.0
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface FindField {
	Class<?> parent() default Object.class;
	String name() default "";
}
