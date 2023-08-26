package ftbsc.lll.processor.tools.containers;

import ftbsc.lll.exceptions.TargetNotFoundException;
import ftbsc.lll.mapper.tools.data.ClassData;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.tools.ProcessorOptions;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.function.Function;

import static ftbsc.lll.processor.tools.ASTUtils.*;

/**
 * Container for information about a class.
 * Used internally for efficiency reasons.
 * @since 0.5.0
 */
public class ClassContainer {
	/**
	 * The {@link ClassData} for the class represented by this container.
	 */
	public final ClassData data;

	/**
	 * The {@link Element} corresponding to the class.
	 * May only be null intentionally i.e. when the associated element is
	 * an anonymous class or a child of an anonymous class.
	 */
	public final Element elem;

	/**
	 * Private constructor, called from {@link #from(Annotation, Function, String, ProcessorOptions)}.
	 * @param fqn the fully-qualified name of the target class
	 * @param innerNames an array of Strings containing the path to the inner class, may be null
	 * @param options the {@link ProcessorOptions} to be used
	 */
	private ClassContainer(String fqn, String[] innerNames, ProcessorOptions options) {
		//find and validate
		Element elem = options.env.getElementUtils().getTypeElement(fqn);

		if(elem == null)
			throw new TargetNotFoundException("class", fqn);

		StringBuilder fqnBuilder = new StringBuilder(
			internalNameFromType(elem.asType(), options.env).replace('/', '.')
		);

		if(innerNames != null) {
			for(String inner : innerNames) {
				if(inner == null) continue;
				fqnBuilder.append("$").append(inner);
				try {
					int anonClassCounter = Integer.parseInt(inner);
					//anonymous classes cannot be validated!
					if(options.anonymousClassWarning)
						options.env.getMessager().printMessage(
							Diagnostic.Kind.WARNING,
							String.format(
								"Anonymous classes cannot be verified by the processor. The existence of %s$%s is not guaranteed!",
								fqnBuilder, anonClassCounter
							)
						);
					elem = null;
					break;
				} catch(NumberFormatException exc) {
					elem = elem
						.getEnclosedElements()
						.stream()
						.filter(e -> e instanceof TypeElement)
						.filter(e -> e.getSimpleName().contentEquals(inner))
						.findFirst()
						.orElse(null);
				}
				if(elem == null)
					throw new TargetNotFoundException("class", inner);
			}
		}
		this.data = getClassData(fqnBuilder.toString(), options.mapper);
		this.elem = elem;
	}

	/**
	 * Safely extracts a {@link Class} from an annotation and gets its fully qualified name.
	 * @param ann the annotation containing the class
	 * @param classFunction the annotation function returning the class
	 * @param innerName a string containing the inner class name or nothing
	 * @param options the {@link ProcessorOptions} to be used
	 * @param <T> the type of the annotation carrying the information
	 * @return the fully qualified name of the given class
	 * @since 0.5.0
	 */
	public static <T extends Annotation> ClassContainer from(T ann, Function<T, Class<?>> classFunction, String innerName, ProcessorOptions options) {
		String fqn;
		String[] inner;
		fqn = getTypeFromAnnotation(ann, classFunction, options.env).toString();
		inner = innerName.equals("") ? null : innerName.split("//$");
		return new ClassContainer(fqn, inner, options);
	}

	/**
	 * Safely extracts a {@link Class} from an annotation and gets its fully qualified name.
	 * @param cl the {@link TypeElement} representing the class
	 * @param options the {@link ProcessorOptions} to be used
	 * @return the fully qualified name of the given class
	 * @since 0.6.0
	 */
	public static ClassContainer from(TypeElement cl, ProcessorOptions options) {
		return new ClassContainer(cl.getQualifiedName().toString(), null, options);
	}

	/**
	 * Finds and builds a {@link ClassContainer} based on information contained
	 * within {@link Patch} or a {@link Find} annotations, else returns a fallback.
	 * @param fallback the {@link ClassContainer} it falls back on
	 * @param p the {@link Patch} annotation to get info from
	 * @param f the {@link Find} annotation to get info from
	 * @param options the {@link ProcessorOptions} to be used
	 * @return the built {@link ClassContainer} or the fallback if not enough information was present
	 * @since 0.5.0
	 */
	public static ClassContainer findOrFallback(ClassContainer fallback, Patch p, Find f, ProcessorOptions options) {
		if(f == null) return ClassContainer.from(p, Patch::value, p.innerName(), options);
		ClassContainer cl = ClassContainer.from(f, Find::value, f.innerName(), options);
		return cl.data.name.equals("java/lang/Object") ? fallback : cl;
	}
}
