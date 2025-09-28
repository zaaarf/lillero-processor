package ftbsc.lll.processor.utils;

import ftbsc.lll.exceptions.*;
import ftbsc.lll.mapper.utils.Mapper;
import ftbsc.lll.mapper.data.ClassData;
import ftbsc.lll.mapper.data.FieldData;
import ftbsc.lll.mapper.data.MethodData;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.containers.ClassContainer;
import ftbsc.lll.proxies.ProxyType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
	public static <T extends Element>  List<T> findAnnotatedEnclosedElements(Element parent, Class<? extends Annotation> ann) {
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
		for(Modifier m : modifiers) i |= mapModifier(m);
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
		T ann,
		Function<T, Class<?>> classFunction,
		ProcessingEnvironment env
	) {
		try {
			String fqn = classFunction.apply(ann).getCanonicalName();
			if(fqn == null) fqn = "";
			return env.getElementUtils().getTypeElement(fqn).asType();
		} catch(MirroredTypeException e) {
			return e.getTypeMirror();
		}
	}

	/**
	 * Gets the internal name from a {@link TypeMirror}.
	 * @param type the {@link TypeMirror} in question
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @return the internal name at compile time, or null if it wasn't a qualifiable
	 * @since 0.5.1
	 */
	public static String internalNameFromType(TypeMirror type, ProcessingEnvironment env) {
		// needed to actually turn elem into a TypeVariable, find it ignoring generics
		Element elem = env.getTypeUtils().asElement(env.getTypeUtils().erasure(type));
		StringBuilder fqnBuilder = new StringBuilder();
		while(elem.getEnclosingElement() != null && elem.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
			fqnBuilder
				.insert(0, elem.getSimpleName().toString())
				.insert(0, "$");
			elem = elem.getEnclosingElement();
		}
		return fqnBuilder
			.insert(0, env.getTypeUtils().erasure(elem.asType()).toString())
			.toString()
			.replace('.', '/');
	}

	/**
	 * Builds a type descriptor from the given {@link TypeMirror}.
	 * @param t the {@link TypeMirror} representing the desired type
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @return a {@link String} containing the relevant descriptor
	 */
	public static String descriptorFromType(TypeMirror t, ProcessingEnvironment env) {
		t = env.getTypeUtils().erasure(t); //type erasure

		StringBuilder desc = new StringBuilder();
		// add array brackets
		while(t.getKind() == TypeKind.ARRAY) {
			desc.append("[");
			t = ((ArrayType) t).getComponentType();
		}

		if(t.getKind() == TypeKind.TYPEVAR)
			t = ((TypeVariable) t).getUpperBound();

		if(t.getKind() == TypeKind.DECLARED)
			desc
				.append("L")
				.append(internalNameFromType(t, env))
				.append(";");
		else {
			switch(t.getKind()) {
				case BOOLEAN:
					desc.append("Z");
					break;
				case CHAR:
					desc.append("C");
					break;
				case BYTE:
					desc.append("B");
					break;
				case SHORT:
					desc.append("S");
					break;
				case INT:
					desc.append("I");
					break;
				case FLOAT:
					desc.append("F");
					break;
				case LONG:
					desc.append("J");
					break;
				case DOUBLE:
					desc.append("D");
					break;
				case VOID:
					desc.append("V");
					break;
			}
		}

		return desc.toString();
	}

	/**
	 * Builds a method descriptor from the given {@link ExecutableElement}.
	 * @param m the {@link ExecutableElement} for the method
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @return a {@link String} containing the relevant descriptor
	 */
	public static String descriptorFromExecutableElement(ExecutableElement m, ProcessingEnvironment env) {
		StringBuilder methodSignature = new StringBuilder();
		methodSignature.append("(");
		m.getParameters().forEach(p -> methodSignature.append(descriptorFromType(p.asType(), env)));
		methodSignature.append(")");
		methodSignature.append(descriptorFromType(m.getReturnType(), env));
		return methodSignature.toString();
	}

	/**
	 * Gets the {@link ClassData} corresponding to the given internal name,
	 * or creates a false one with the same, non-obfuscated name twice.
	 * @param name the internal name of the class to convert
	 * @param mapper the {@link Mapper} to use, may be null
	 * @return the fully qualified class name
	 * @since 0.6.1
	 */
	public static ClassData getClassData(String name, Mapper mapper) {
		try {
			name = name.replace('.', '/'); // just in case
			if(mapper != null) {
				return mapper.getClassData(name);
			}
		} catch(MappingNotFoundException ignored) {}
		return new ClassData(name, name);
	}

	/**
	 * Gets the {@link MethodData} corresponding to the method matching the given
	 * name, parent and descriptor, or creates a dummy one with fake data if no
	 * valid mapping is found.
	 * @param parent the internal name of the parent class
	 * @param name the name of the member
	 * @param descriptor the descriptor of the method
	 * @param mapper the {@link Mapper} to use, may be null
	 * @param outcome an array whose first element will be set to false if an exception is thrown, may be null
	 * @return the method data
	 * @since 0.6.1
	 */
	public static MethodData getMethodData(
		String parent,
		String name,
		String descriptor,
		Mapper mapper,
		boolean[] outcome // janky but it's the lesser evil
	) {
		try {
			parent = parent.replace('.', '/'); // just in case
			if(mapper != null) {
				return mapper.getMethodData(parent, name, descriptor);
			}
		} catch(MappingNotFoundException ex) {
			if(outcome != null && outcome.length >= 1) {
				outcome[0] = false;
			}
		}

		return new MethodData(getClassData(name, mapper), name, name, descriptor);
	}

	/**
	 * Gets the {@link FieldData} corresponding to the field matching the given
	 * name and parent, or creates a dummy one with fake data if no valid
	 * mapping is found.
	 * @param parent the internal name of the parent class
	 * @param name the name of the member
	 * @param mapper the {@link Mapper} to use, may be null
	 * @return the field data
	 * @since 0.6.1
	 */
	public static FieldData getFieldData(String parent, String name, Mapper mapper) {
		try {
			name = name.replace('.', '/'); // just in case
			if(mapper != null) {
				return mapper.getFieldData(parent, name);
			}
		} catch(MappingNotFoundException ignored) {}
		return new FieldData(getClassData(name, mapper), name, name);
	}

	/**
	 * Finds a member given the name, the container class and (if it's a method) the descriptor.
	 * @param parent the {@link ClassContainer} representing the parent
	 * @param name the name to search for
	 * @param descr the descriptor to search for, or null if it's not a method
	 * @param strict whether to perform lookup in strict mode (see {@link Target#strict()} for more information)
	 * @param field whether the member being searched is a field
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @return the desired member, if it exists
	 * @throws AmbiguousDefinitionException if it finds more than one candidate
	 * @throws TargetNotFoundException if it finds no valid candidate
	 * @since 0.3.0
	 */
	public static Element findMember(
		ClassContainer parent,
		String name,
		String descr,
		boolean strict,
		boolean field,
		ProcessingEnvironment env
	) {
		if(parent.elem == null) {
			throw new TargetNotFoundException("parent class", parent.data.name);
		}

		// try to find by name
		List<Element> candidates = parent.elem.getEnclosedElements()
			.stream()
			.filter(e -> (field && e instanceof VariableElement) || e instanceof ExecutableElement)
			.filter(e -> e.getSimpleName().contentEquals(name))
			.collect(Collectors.toList());

		if(candidates.isEmpty()) {
			throw new TargetNotFoundException(field ? "field" : "method", name, parent.data.name);
		}

		if(candidates.size() == 1 && (!strict || descr == null)) {
			return candidates.get(0);
		}

		if(descr == null) {
			throw new AmbiguousDefinitionException(String.format(
				"Found %d members named %s in class %s!",
				candidates.size(),
				name,
				parent.data.name
			));
		} else {
			if(field) {
				// fields can verify the signature for extra safety
				// but there can only be 1 field with a given name
				if(!descriptorFromType(candidates.get(0).asType(), env).equals(descr)) {
					throw new TargetNotFoundException(
						"field",
						String.format("%s with descriptor %s", name, descr),
						parent.data.name
					);
				}
			} else {
				candidates = candidates.stream()
					.map(e -> (ExecutableElement) e)
					.filter(strict
						? c -> descr.equals(descriptorFromExecutableElement(c, env))
						: c -> descr.split("\\)")[0].equalsIgnoreCase(
							descriptorFromExecutableElement(c, env).split("\\)")[0]
						)
					).collect(Collectors.toList());
			}

			if(candidates.isEmpty()) {
				throw new TargetNotFoundException(
					"method",
					String.format("%s %s", name, descr),
					parent.data.name
				);
			}

			if(candidates.size() > 1) {
				throw new AmbiguousDefinitionException(String.format(
					"Found %d methods named %s in class %s!",
					candidates.size(),
					name,
					parent.data.name
				));
			}
			return candidates.get(0);
		}
	}

	/**
	 * Tries to find the method being overridden by the given {@link ExecutableElement}.
	 * In case of multiple layers of overriding, it finds the original one. In case of
	 * no overriding, it returns the given method.
	 * @param context the {@link TypeElement} representing the parent class
	 * @param method an {@link ExecutableElement} representing the overriding method
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @return the original overridden method, or the given method if it was not found
	 * @since 0.5.2
	 */
	public static ExecutableElement findOverriddenMethod(
		TypeElement context,
		ExecutableElement method,
		ProcessingEnvironment env
	) {
		for(Element elem : context.getEnclosedElements()) {
			if(elem.getKind() != ElementKind.METHOD) continue;
			if(env.getElementUtils().overrides(
				method,
				(ExecutableElement) elem,
				(TypeElement) method.getEnclosingElement()
			)) {
				method = (ExecutableElement) elem;
				break; // found
			}
		}

		List<TypeElement> potentialDeclarers = new ArrayList<>();

		if(context.getSuperclass().getKind() != TypeKind.NONE) {
			potentialDeclarers.add((TypeElement) env.getTypeUtils().asElement(context.getSuperclass()));
		}

		for(TypeMirror i : context.getInterfaces()) {
			if(i.getKind() != TypeKind.NONE) {
				potentialDeclarers.add((TypeElement) env.getTypeUtils().asElement(i));
			}
		}

		// don't recurse above your pay grade
		potentialDeclarers.removeIf(d -> d.getQualifiedName().contentEquals("java.lang.Object"));

		for(TypeElement declarer : potentialDeclarers) {
			ExecutableElement found = findOverriddenMethod(declarer, method, env);
			if(!found.equals(method)) {
				method = found;
				break;
			}
		}

		return method;
	}

	/**
	 * Tries to find the "synthetic bridge" generated by the compiler for a certain overridden
	 * methods. A "bridge" only exists in cases where type erasure is involved (i.e. when the
	 * method being overridden uses a generic parameter that is not preserved in the overriding
	 * method).
	 * @param method an {@link ExecutableElement} the (potentially) bridged method
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @return the "bridge", or null if not found
	 * @throws TargetNotFoundException if the method in question was not overriding anything, or
	 * 																 if the method it was overriding does not require a bridge
	 * @since 0.5.2
	 */
	public static ExecutableElement findSyntheticBridge(
		ExecutableElement method,
		ProcessingEnvironment env
	) throws TargetNotFoundException {
		TypeElement parent = (TypeElement) method.getEnclosingElement();
		ExecutableElement overriding = findOverriddenMethod(parent, method, env);
		if(descriptorFromExecutableElement(overriding, env).equals(descriptorFromExecutableElement(method, env)))
			throw new TargetNotFoundException(
				"bridge method for",
				overriding.getSimpleName().toString(),
				parent.getQualifiedName().toString()
			);
		else return overriding;
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

	/**
	 * A pattern for efficiently recognising numeric strings.
	 * @since 0.7.0
	 */
	private final static Pattern NUMERIC = Pattern.compile("^[0-9]+$");

	/**
	 * Checks whether a certain class name is valid, and whether the processor is able to validate
	 * its existence.
	 * @param name the name to validate
	 * @return true if it's a valid class name, false if it's an anonymous class identifier
	 * @throws InvalidClassNameException if an invalid name was provided
	 * @since 0.7.0
	 */
	public static boolean shouldValidate(String name) throws InvalidClassNameException {
		if(SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name)) {
			return true; // if it's a valid name, proceed
		} else if(NUMERIC.matcher(name).matches()) {
			return false;
		} else throw new InvalidClassNameException(name);
	}

	/**
	 * Attempts to match a {@link Target} method.
	 * @param parent the injector class
	 * @param target the target method
	 * @param targetAnn the {@link Target} annotation
	 * @param injectorCandidates the injector candidates
	 * @param finderCandidates the finder candidates
	 * @param processingEnv the processing environment
	 * @return a {@link ExecutableElement} if an injector was matched,
	 *         a {@link VariableElement} if a finder was matched,
	 *         or null if this was an orphan
	 * @throws AmbiguousDefinitionException if the definition is ambiguous
	 */
	public static Element matchTarget(
		TypeElement parent,
		ExecutableElement target,
		Target targetAnn,
		List<ExecutableElement> injectorCandidates,
		List<VariableElement> finderCandidates,
		ProcessingEnvironment processingEnv
	) {
		// find target by name
		injectorCandidates =
			injectorCandidates
				.stream()
				.filter(i -> i.getSimpleName().contentEquals(targetAnn.of()))
				.collect(Collectors.toList());
		finderCandidates =
			finderCandidates
				.stream()
				.filter(i -> i.getSimpleName().contentEquals(targetAnn.of()))
				.collect(Collectors.toList());

		// throw exception if user is a moron and defined a finder and an injector with the same name
		if(!finderCandidates.isEmpty() && !injectorCandidates.isEmpty()) {
			throw new AmbiguousDefinitionException(
				String.format("Target specified user %s, but name was used by both a finder and injector.", targetAnn.of())
			);
		} else if(finderCandidates.isEmpty() && injectorCandidates.isEmpty()) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
				String.format(
					"Found orphan @Target annotation on method %s.%s pointing at method %s, it will be ignored!",
					parent.getSimpleName().toString(),
					target.getSimpleName().toString(),
					targetAnn.of()
				)
			);
			return null;
		} else if(finderCandidates.isEmpty() && injectorCandidates.size() != 1) {
			throw new AmbiguousDefinitionException(
				String.format("Found multiple candidate injectors for target %s::%s!", parent.getSimpleName(), target.getSimpleName())
			);
		} else if(injectorCandidates.isEmpty() && finderCandidates.size() != 1) {
			throw new AmbiguousDefinitionException(
				String.format("Found multiple candidate finders for target %s::%s!", parent.getSimpleName(), target.getSimpleName())
			);
		} else {
			if(injectorCandidates.size() == 1) {
				return injectorCandidates.get(0);
			} else {
				return finderCandidates.get(0);
			}
		}
	}
}
