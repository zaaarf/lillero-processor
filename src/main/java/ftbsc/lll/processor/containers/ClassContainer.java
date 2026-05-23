package ftbsc.lll.processor.containers;

import ftbsc.lll.processor.reporting.ErrorReporter;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.ProcessorOptions;
import ftbsc.lll.processor.reporting.MemberType;

import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.function.Function;

import static ftbsc.lll.processor.utils.ASTUtils.*;

/**
 * Container for information about a class.
 * Used internally for efficiency reasons.
 * @since 0.5.0
 */
public class ClassContainer {
	/**
	 * The name of the class.
	 */
	public final String name;

	/**
	 * The mapped name of the class.
	 * Will be identical to {@link #name} if no mappings were given.
	 */
	public final String nameMapped;

	/**
	 * The {@link TypeElement} corresponding to the class.
	 * May only be null intentionally i.e. when the associated element is
	 * an anonymous class or a child of an anonymous class.
	 */
	public final TypeElement elem;

	/**
	 * Private constructor, called from the factory methods.
	 * @param fqn the fully-qualified name of the target class
	 * @param innerNames an array of Strings containing the path to the inner class, may be null
	 * @param options the {@link ProcessorOptions} to be used
	 * @param manual whether this fully-qualified was manually inputed by the user
	 */
	private ClassContainer(String fqn, String[] innerNames, ProcessorOptions options, boolean manual) {
		// find and validate
		TypeElement elem = options.env.getElementUtils().getTypeElement(fqn);

		if(elem == null) {
			if(manual) {
				if(options.manualClassWarning) {
					options.env.getMessager().printMessage(
						Diagnostic.Kind.WARNING,
						String.format("Manually-specified class %s and its children cannot not be verified by the processor!", fqn)
					);
				}
			} else {
				throw ErrorReporter.notFound(MemberType.CLASS, fqn, null);
			}
		}

		StringBuilder fqnBuilder = new StringBuilder(
			manual ? fqn : internalNameFromType(elem.asType(), options.env).replace('/', '.')
		);

		if(innerNames != null) {
			boolean skip = false;
			for(String inner : innerNames) {
				if(inner != null) fqnBuilder.append('$').append(inner);
				if(skip) continue;
				if(elem != null && shouldValidate(inner)) {
					elem = elem
						.getEnclosedElements()
						.stream()
						.filter(e -> e instanceof TypeElement)
						.map(e -> (TypeElement) e)
						.filter(e -> e.getSimpleName().contentEquals(inner))
						.findFirst()
						.orElse(null);
				} else {
					if(options.anonymousClassWarning) {
						options.env.getMessager().printMessage(
							Diagnostic.Kind.WARNING,
							String.format(
								"Anonymous class %s$%s and its children cannot be verified by the processor!",
								fqnBuilder,
								inner
							)
						);
					}

					elem = null;
					skip = true;
					continue;
				}

				if(elem == null) {
					throw ErrorReporter.notFound(MemberType.CLASS, fqnBuilder.toString(), null);
				}
			}
		}

		this.name = fqnBuilder.toString().replace('.', '/');
		this.nameMapped = options.mapper.mapClass(this.name);
		this.elem = elem;
	}

	/**
	 * Safely extracts a {@link Class} from an annotation and gets its fully qualified name.
	 * @param ann the annotation containing the class
	 * @param classFunction the annotation function returning the class
	 * @param fqn the fully qualified name to use as override
	 * @param innerNames a string containing the inner class name or nothing
	 * @param options the {@link ProcessorOptions} to be used
	 * @param <T> the type of the annotation carrying the information
	 * @return the fully qualified name of the given class
	 * @since 0.5.0
	 */
	public static <T extends Annotation> ClassContainer from(
		T ann,
		Function<T, Class<?>> classFunction,
		String fqn,
		String[] innerNames,
		ProcessorOptions options
	) {
		String chosenFqn = fqn.isEmpty()
			? getTypeFromAnnotation(ann, classFunction, options.env).toString()
			: fqn;
		String[] inner = innerNames != null && innerNames.length != 0
			? String.join("$", innerNames).split("\\$")
			: null;
		return new ClassContainer(chosenFqn, inner, options, !fqn.isEmpty());
	}

	/**
	 * Finds and builds a {@link ClassContainer} based on information contained
	 * within {@link Patch} or a {@link Find} annotations, else returns a fallback.
	 * @param fallback the {@link TypeElement} it falls back on
	 * @param p the {@link Patch} annotation to get info from
	 * @param f the {@link Find} annotation to get info from
	 * @param options the {@link ProcessorOptions} to be used
	 * @return the built {@link ClassContainer} or the fallback if not enough information was present
	 * @since 0.5.0
	 */
	public static ClassContainer findOrFallback(TypeElement fallback, Patch p, Find f, ProcessorOptions options) {
		if(f == null) {
			return ClassContainer.from(p, Patch::value, p.fqn(), p.inner(), options);
		}

		ClassContainer cl = ClassContainer.from(f, Find::value, f.fqn(), f.inner(), options);
		return cl.name.equals("java/lang/Object")
			? describe(fallback, options)
			: cl;
	}

	/**
	 * Creates a {@link ClassContainer} describing the given {@link TypeElement}.
	 * @param type the {@link TypeElement} to describe
	 * @param options the {@link ProcessorOptions} to be used
	 * @return a {@link ClassContainer} describing the fallback
	 * @since 0.9.2
	 */
	public static ClassContainer describe(TypeElement type, ProcessorOptions options) {
		return new ClassContainer(type.getQualifiedName().toString(), null, options, false);
	}
}
