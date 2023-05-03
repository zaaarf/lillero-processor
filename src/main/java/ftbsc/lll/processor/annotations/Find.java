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
@java.lang.annotation.Target(ElementType.FIELD)
public @interface Find {
	/**
	 * @return the {@link Class} object containing the target, or the
	 * {@link Object} class if not specified (the annotation's parent
	 * class is instead used).
	 * @since 0.5.0
	 */
	Class<?> value() default Object.class;

	/**
	 * This is the inner class name to append after a $ symbol to the already acquired
	 * fully-qualified name. If it's a number instead of a valid name, the class will be
	 * treated as an anonymous class, and will therefore be automatically unverified.
	 * For a {@link TypeProxy}, this refers to the class itself rather than the parent.
	 * @return the name of the inner class that contains the target, defaults to empty
	 *         string (not an inner class)
	 * @since 0.5.0
	 */
	String innerName() default "";

	/**
	 * For a {@link FieldProxy}, this is the name of the field to find. If omitted,
	 * it will fall back on the name of the annotated field.
	 * For a {@link MethodProxy} it indicates an attempt to match by name only, with
	 * this name. This will issue a warning unless warnings are disabled. It will fail
	 * and throw an exception if multiple methods with that name are found in the
	 * relevant class. It is generally recommended that you use a @link Target} stub
	 * for methods, as this can lead to unpredictable behaviour at runtime.
	 * It will have no effect on a {@link TypeProxy}.
	 * @return the name of the target, will default to the empty string (the name of
	 *         the annotated method will instead be used).
	 * @since 0.5.0
	 */
	String name() default "";

	/**
	 * This overrules the type of a field. Only to be used in the case (such as fields of
	 * anonymous classes) of fields whose parents cannot be reached at processing time.
	 * @return a {@link Class} representing the type.
	 * @deprecated This is only meant as a temporary solution until a better handling
	 * 					   is implemented; only use this if strictly necessary as it may be
	 * 					   removed or changed even across revisions.
	 */
	@Deprecated
	Class<?> type() default Object.class;

	/**
	 * @return the inner class name to be used with {@link #type()}
	 * @deprecated See {@link #type()}'s deprecation notice for more info.
	 */
	@Deprecated
	String typeInner() default "";
}
