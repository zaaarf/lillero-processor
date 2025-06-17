package ftbsc.lll.processor.annotations;

import ftbsc.lll.proxies.impl.FieldProxy;
import ftbsc.lll.proxies.impl.MethodProxy;
import ftbsc.lll.proxies.impl.TypeProxy;

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
@java.lang.annotation.Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface Find {
	/**
	 * @return the {@link Class} object containing the target; the annotation's parent
	 *         class is instead used
	 * @since 0.5.0
	 */
	Class<?> value() default Object.class;

	/**
	 * @return the fully-qualified name of container, which overrides {@link #value()}
	 * @since 0.8.8
	 * @see Patch#fqn() for details
	 */
	String fqn() default "";

	/**
	 * For a {@link TypeProxy}, this refers to the target itself rather than its parent.
	 * @return the name of the inner class that contains the target
	 * @see Patch#inner() for details
	 * @since 0.5.0
	 */
	String[] inner() default {};

	/**
	 * For a {@link FieldProxy}, this is the name of the field to find. If omitted,
	 * it will fall back on the name of the annotated field.
	 * For a {@link MethodProxy} it indicates an attempt to match by name only, with
	 * this name. This will issue a warning unless warnings are disabled. It will fail
	 * and throw an exception if multiple methods with that name are found in the
	 * relevant class. It is generally recommended that you use a {@link Target} stub
	 * for methods, as this can lead to unpredictable behaviour at runtime.
	 * It will have no effect on a {@link TypeProxy}.
	 * @return the name of the target, will default to the empty string (the name of
	 *         the annotated method will instead be used)
	 * @since 0.5.0
	 */
	String name() default "";

	/**
	 * This overrules a field type. Only to be used in the case (such as in fields of
	 * anonymous classes) of fields whose parents cannot be reached at processing time.
	 * @return a {@link Class} representing the type
	 */
	Class<?> type() default Object.class;

	/**
	 * @return the fully-qualified name of the target type, which overrides {@link #type()}
	 * @since 0.8.8
	 * @see Patch#fqn() for details
	 */
	String typeFqn() default "";

	/**
	 * This is to be used in cases where private inner classes are used as parameters.
	 * @return the inner class name to be used with {@link #type()}
	 * @see Patch#inner() for details
	 */
	String[] typeInner() default {};
}
