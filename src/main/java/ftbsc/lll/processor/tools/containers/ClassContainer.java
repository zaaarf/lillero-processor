package ftbsc.lll.processor.tools.containers;

import ftbsc.lll.exceptions.TargetNotFoundException;
import ftbsc.lll.processor.LilleroProcessor;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.function.Function;

import static ftbsc.lll.processor.tools.ASTUtils.*;

/**
 * Container for information about a class.
 * Used internally for efficiency reasons.
 * @since 0.5.0
 */
public class ClassContainer {
	/**
	 * The fully-qualified name of the class.
	 */
	public final String fqn;

	/**
	 * The obfuscated fully-qualified name of the class.
	 * If the mapper passed is null, then this will be identical to {@link #fqn}
	 */
	public final String fqnObf;

	/**
	 * The {@link Element} corresponding to the class.
	 * May only be null intentionally i.e. when the associated element is
	 * an anonymous class or a child of an anonymous class.
	 */
	public final Element elem;

	/**
	 * Public constructor.
	 * @param fqn the fully-qualified name of the target class
	 * @param innerNames an array of Strings containing the path to the inner class, may be null
	 * @param env the {@link ProcessingEnvironment} to be used to locate the class
	 * @param mapper the {@link ObfuscationMapper} to be used, may be null
	 */
	public ClassContainer(String fqn, String[] innerNames, ProcessingEnvironment env, ObfuscationMapper mapper) {
		//find and validate
		Element elem = env.getElementUtils().getTypeElement(fqn);

		if(elem == null)
			throw new TargetNotFoundException("class", fqn);

		StringBuilder fqnBuilder = new StringBuilder(internalNameFromElement(elem, env));

		if(innerNames != null) {
			for(String inner : innerNames) {
				if(inner == null) continue;
				fqnBuilder.append("$").append(inner);
				try {
					int anonClassCounter = Integer.parseInt(inner);
					//anonymous classes cannot be validated!
					if(LilleroProcessor.anonymousClassWarning)
						env.getMessager().printMessage(
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

		this.fqn = fqnBuilder.toString();
		this.fqnObf = findClassName(this.fqn, mapper);
		this.elem = elem;
	}

	/**
	 * Safely extracts a {@link Class} from an annotation and gets its fully qualified name.
	 * @param ann the annotation containing the class
	 * @param classFunction the annotation function returning the class
	 * @param className a string containing the FQN, the inner class name or nothing
	 * @param env the {@link ProcessingEnvironment} to be used to locate the class
	 * @param mapper the {@link ObfuscationMapper} to be used, may be null
	 * @param <T> the type of the annotation carrying the information
	 * @return the fully qualified name of the given class
	 * @since 0.5.0
	 */
	public static <T extends Annotation> ClassContainer from(
		T ann, Function<T, Class<?>> classFunction, String className,
		ProcessingEnvironment env, ObfuscationMapper mapper)
	{
		String fqn;
		String[] inner;
		if(className.contains(".")) {
			String[] split = className.split("//$");
			fqn = split[0];
			inner = split.length == 1 ? null : Arrays.copyOfRange(split, 1, split.length - 1);
		} else {
			fqn = getTypeFromAnnotation(ann, classFunction, env).toString();
			inner = className.equals("") ? null : className.split("//$");
		}

		return new ClassContainer(fqn, inner, env, mapper);
	}

	/**
	 * Finds and builds a {@link ClassContainer} based on information contained
	 * within a {@link Find} annotation, else returns a fallback.
	 * @param fallback the {@link ClassContainer} it falls back on
	 * @param f the {@link Find} annotation to get info from
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @param mapper the {@link ObfuscationMapper} to use, may be null
	 * @return the built {@link ClassContainer} or the fallback if not enough information was present
	 * @since 0.5.0
	 */
	public static ClassContainer findOrFallback(ClassContainer fallback, Find f, ProcessingEnvironment env, ObfuscationMapper mapper) {
		if(f == null) return fallback;
		ClassContainer cl = ClassContainer.from(f, Find::value, f.className(), env, mapper);
		return cl.fqn.equals("java.lang.Object")
			? fallback
			: cl;
	}
}