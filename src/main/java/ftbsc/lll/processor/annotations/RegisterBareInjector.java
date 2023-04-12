package ftbsc.lll.processor.annotations;

import ftbsc.lll.IInjector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks the class in question as a class to be added to the service provider file
 * (META-INF/services/ftbsc.lll.IInjector) without actually processing it. This can
 * be used to mix in a same project regular {@link IInjector}s and those generated
 * by the processor.
 * @since 0.6.0
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.TYPE)
public @interface RegisterBareInjector {}
