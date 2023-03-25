package ftbsc.lll.processor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to support {@link Target} as a {@link Repeatable} annotation.
 * @since 0.5.0
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface MultipleTargets {
	/**
	 * @return the {@link Injector} annotations, as an array
	 */
	Target[] value();
}
