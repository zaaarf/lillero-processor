package ftbsc.lll.processor.tools;

import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.exceptions.MappingNotFoundException;
import ftbsc.lll.exceptions.NotAProxyException;
import ftbsc.lll.exceptions.TargetNotFoundException;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.tools.containers.ClassContainer;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;
import ftbsc.lll.proxies.ProxyType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ftbsc.lll.processor.tools.JavaPoetUtils.descriptorFromExecutableElement;
import static ftbsc.lll.processor.tools.JavaPoetUtils.descriptorFromType;

/**
 * Collection of AST-related static utils that didn't really fit into the main class.
 */
public class ASTUtils {
	/**
	 * Finds, among the methods of a class cl, the one annotated with ann, and tries to build
	 * an {@link Element} from it.
	 * @param parent the parent {@link Element} to the desired element
	 * @param ann the {@link Class} corresponding to the desired annotation
	 * @param <T> the type of {@link Element} to use
	 * @return a {@link List} of {@link Element}s annotated with the given annotation
	 * @since 0.2.0
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Element>  List<T> findAnnotatedElement(Element parent, Class<? extends Annotation> ann) {
		return parent.getEnclosedElements()
			.stream()
			.filter(e -> e.getAnnotationsByType(ann).length != 0)
			.map(e -> (T) e)
			.collect(Collectors.toList());
	}

	/**
	 * Maps a {@link javax.lang.model.element.Modifier} to its reflective
	 * {@link java.lang.reflect.Modifier} equivalent.
	 * @param m the {@link Modifier} to map
	 * @return an integer representing the modifier
	 * @see java.lang.reflect.Modifier
	 * @since 0.2.0
	 */
	public static int mapModifier(Modifier m) {
		switch(m) {
			case PUBLIC:
				return java.lang.reflect.Modifier.PUBLIC;
			case PROTECTED:
				return java.lang.reflect.Modifier.PROTECTED;
			case PRIVATE:
				return java.lang.reflect.Modifier.PRIVATE;
			case ABSTRACT:
				return java.lang.reflect.Modifier.ABSTRACT;
			case STATIC:
				return java.lang.reflect.Modifier.STATIC;
			case FINAL:
				return java.lang.reflect.Modifier.FINAL;
			case TRANSIENT:
				return java.lang.reflect.Modifier.TRANSIENT;
			case VOLATILE:
				return java.lang.reflect.Modifier.VOLATILE;
			case SYNCHRONIZED:
				return java.lang.reflect.Modifier.SYNCHRONIZED;
			case NATIVE:
				return java.lang.reflect.Modifier.NATIVE;
			case STRICTFP:
				return java.lang.reflect.Modifier.STRICT;
			default:
				return 0;
		}
	}

	/**
	 * Takes in a {@link Collection} of AST {@link Modifier}s and
	 * returns them mapped to their reflective integer equivalent.
	 * @param modifiers the {@link Modifier}s
	 * @return an integer value representing them
	 * @since 0.5.0
	 */
	public static int mapModifiers(Collection<Modifier> modifiers) {
		int i = 0;
		for(Modifier m : modifiers)
			i |= mapModifier(m);
		return i;
	}

	/**
	 * Safely extracts a {@link Class} from an annotation and gets a {@link TypeMirror} representing it.
	 * @param ann the annotation containing the class
	 * @param classFunction the annotation function returning the class
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @param <T> the type of the annotation carrying the information
	 * @return a {@link TypeMirror} representing the requested {@link Class}
	 * @since 0.3.0
	 */
	public static <T extends Annotation> TypeMirror getTypeFromAnnotation(
		T ann, Function<T, Class<?>> classFunction, ProcessingEnvironment env) {
		try {
			String fqn = classFunction.apply(ann).getCanonicalName();
			return env.getElementUtils().getTypeElement(fqn).asType();
		} catch(MirroredTypeException e) {
			return e.getTypeMirror();
		}
	}

	/**
	 * Finds the class name and maps it to the correct format.
	 * @param name the fully qualified name of the class to convert
	 * @param mapper the {@link ObfuscationMapper} to use, may be null
	 * @return the fully qualified class name
	 * @since 0.3.0
	 */
	public static String findClassName(String name, ObfuscationMapper mapper) {
		try {
			return mapper == null ? name : mapper.obfuscateClass(name).replace('/', '.');
		} catch(MappingNotFoundException e) {
			return name;
		}
	}

	/**
	 * Finds the member name and maps it to the correct format.
	 * @param parentFQN the unobfuscated FQN of the parent class
	 * @param memberName the name of the member
	 * @param methodDescriptor the descriptor of the method, may be null
	 * @param mapper the {@link ObfuscationMapper} to use, may be null
	 * @return the internal class name
	 * @since 0.3.0
	 */
	public static String findMemberName(String parentFQN, String memberName, String methodDescriptor, ObfuscationMapper mapper) {
		try {
			return mapper == null ? memberName : mapper.obfuscateMember(parentFQN, memberName, methodDescriptor);
		} catch(MappingNotFoundException e) {
			return memberName;
		}
	}

	/**
	 * Finds a member given the name, the container class and (if it's a method) the descriptor.
	 * @param parent the {@link ClassContainer} representing the parent
	 * @param name the name to search for
	 * @param descr the descriptor to search for, or null if it's not a method
	 * @param strict whether to perform lookup in strict mode (see {@link Target#strict()} for more information)
	 * @param field whether the member being searched is a field
	 * @return the desired member, if it exists
	 * @throws AmbiguousDefinitionException if it finds more than one candidate
	 * @throws TargetNotFoundException if it finds no valid candidate
	 * @since 0.3.0
	 */
	public static Element findMember(ClassContainer parent, String name, String descr, boolean strict, boolean field) {
		if(parent.elem == null)
			throw new TargetNotFoundException("parent class", parent.fqn);
		//try to find by name
		List<Element> candidates = parent.elem.getEnclosedElements()
			.stream()
			.filter(e -> (field && e instanceof VariableElement) || e instanceof ExecutableElement)
			.filter(e -> e.getSimpleName().contentEquals(name))
			.collect(Collectors.toList());

		if(candidates.size() == 0)
			throw new TargetNotFoundException(field ? "field" : "method", name, parent.fqn);

		if(candidates.size() == 1 && (!strict || descr == null))
			return candidates.get(0);

		if(descr == null) {
			throw new AmbiguousDefinitionException(
				String.format("Found %d members named %s in class %s!", candidates.size(), name, parent.fqn)
			);
		} else {
			if(field) {
				//fields can verify the signature for extra safety
				//but there can only be 1 field with a given name
				if(!descriptorFromType(candidates.get(0).asType()).equals(descr))
					throw new TargetNotFoundException("field", String.format("%s with descriptor %s", name, descr), parent.fqn);
			} else {
				candidates = candidates.stream()
					.map(e -> (ExecutableElement) e)
					.filter(strict
						? c -> descr.equals(descriptorFromExecutableElement(c))
						: c -> descr.split("\\)")[0].equalsIgnoreCase(descriptorFromExecutableElement(c).split("\\)")[0])
					).collect(Collectors.toList());
			}
			if(candidates.size() == 0)
				throw new TargetNotFoundException("method", String.format("%s %s", name, descr), parent.fqn);
			if(candidates.size() > 1)
				throw new AmbiguousDefinitionException(
					String.format("Found %d methods named %s in class %s!", candidates.size(), name, parent.fqn)
				);
			return candidates.get(0);
		}
	}

	/**
	 * Utility method for finding out what type of proxy a field is.
	 * It will fail if the return type is not a known type of proxy.
	 * @param v the annotated {@link VariableElement}
	 * @return the {@link ProxyType} for the element
	 * @throws NotAProxyException if it's neither
	 * @since 0.4.0
	 */
	public static ProxyType getProxyType(VariableElement v) {
		String returnTypeFQN = v.asType().toString();
		switch(returnTypeFQN) {
			case "ftbsc.lll.proxies.impl.FieldProxy":
				return ProxyType.FIELD;
			case "ftbsc.lll.proxies.impl.MethodProxy":
				return ProxyType.METHOD;
			case "ftbsc.lll.proxies.impl.TypeProxy":
				return ProxyType.TYPE;
			case "ftbsc.lll.proxies.impl.PackageProxy":
				return ProxyType.PACKAGE;
			default:
				throw new NotAProxyException(v.getEnclosingElement().getSimpleName().toString(), v.getSimpleName().toString());
		}
	}
}
