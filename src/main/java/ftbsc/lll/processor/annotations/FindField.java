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
	/**
	 * @return the {@link Class} object containing the desired field,
	 * or the {@link Object} class if not specified (the {@link Class}
	 * from {@link Patch#value()} is instead used)
	 */
	Class<?> parent() default Object.class;

	/**
	 * @return the name of the field, will default to the empty string
	 * (the name of the annotated method will instead be used)
	 */
	String name() default "";
}
