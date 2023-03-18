package ftbsc.lll.processor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a method as the injector method for purposes of generation.
 * The method itself should take in a ClassNode and MethodNode (from the ObjectWeb ASM library)
 * as parameters. It will be discarded otherwise.
 * It will also be discarded unless the containing class is annotated with {@link Patch}
 * and another method within the class is annotated with {@link Target}.
 * This annotation may be added multiple times, in order to target multiple methods.
 * @see Patch
 * @see Target
 */
@Retention(RetentionPolicy.CLASS)
@Repeatable(MultipleInjectors.class)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface Injector {
	/**
	 * @return the name of the stub annotated with {@link Target} this is referring to.
	 * @since 0.3.0
	 */
	String targetName() default "";

	/**
	 * @return the parameters of the stub annotated with {@link Target} this is referring
	 * to (used to discern in case of method stubs by the same name)
	 * @since 0.3.0
	 */
	Class<?>[] params() default {};
}
