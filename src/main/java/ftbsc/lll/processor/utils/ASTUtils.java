package ftbsc.lll.processor.utils;

import ftbsc.lll.processor.reporting.ErrorReporter;
import ftbsc.lll.processor.ProcessorOptions;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.containers.ClassContainer;
import ftbsc.lll.processor.reporting.MemberType;
import ftbsc.lll.processor.reporting.Reportable;
import ftbsc.lll.proxies.ProxyType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;
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
				.insert(0, elem.getSimpleName())
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
	 * Checks that a given {@link Element} is accessible from a certain {@link TypeElement} context.
	 * @param member the {@link Element} to check
	 * @param from the {@link TypeElement} to try and access from
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @return true if it was accessible
	 * @since 0.9.2
	 */
	public static boolean isAccessibleFrom(Element member, TypeElement from, ProcessingEnvironment env) {
		if(member instanceof PackageElement) {
			return true;
		}

		// check visibility of enclosing member
		Element enclosing = member.getEnclosingElement();
		if(enclosing instanceof TypeElement || enclosing instanceof PackageElement) {
			if(!isAccessibleFrom(enclosing, from, env)) {
				return false; // enclosing type not visible = member not visible
			}
		} else { // only types and packages may have externally visible children
			return false;
		}

		Modifier visibilityMod = getVisibilityModifier(member);
		if(Modifier.PUBLIC.equals(visibilityMod)) {
			return true; // always visible
		}

		PackageElement memberPkg = env.getElementUtils().getPackageOf(member);
		PackageElement fromPkg = env.getElementUtils().getPackageOf(from);

		if(Modifier.PRIVATE.equals(visibilityMod)) {
			// only if they are within the same class
			return getTopLevel(enclosing).equals(getTopLevel(from));
		}

		if(Modifier.PROTECTED.equals(visibilityMod)) {
			if(memberPkg.equals(fromPkg)) {
				return true; // same package
			}

			if(enclosing instanceof TypeElement) {
				TypeMirror enclosingType = enclosing.asType();
				TypeElement cursor = from;
				while(cursor != null) {
					if(env.getTypeUtils().isSubtype(cursor.asType(), enclosingType)) {
						// either TypeMirror is a subtype or is within a subtype
						return true;
					}

					Element parent = cursor.getEnclosingElement();
					cursor = (parent instanceof TypeElement)
						? (TypeElement) parent
						: null;
				}
			}
			return false;
		}

		// package-private (only within the same package)
		return memberPkg.equals(fromPkg);
	}

	/**
	 * Returns the visibility modifier of the given element.
	 * In this context, a return value of null implies "package-private".
	 * @param elem the element to examine
	 * @return the visibility modifier, or null if none was found
	 * @since 0.9.8
	 */
	public static Modifier getVisibilityModifier(Element elem) {
		Set<Modifier> mods = elem.getModifiers();
		if(mods.contains(Modifier.PUBLIC)) {
			return Modifier.PUBLIC;
		} else if(mods.contains(Modifier.PROTECTED)) {
			return Modifier.PROTECTED;
		} else if(mods.contains(Modifier.PRIVATE)) {
			return Modifier.PRIVATE;
		} else {
			return null;
		}
	}

	/**
	 * Finds the top-level class of a given {@link Element}.
	 * The parent of this is guaranteed to be a {@link PackageElement}.
	 * @param e the {@link Element} to look it up for
	 * @return the top-level containing class
	 * @since 0.9.2
	 */
	public static Element getTopLevel(Element e) {
		while(e.getEnclosingElement() != null && e.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
			e = e.getEnclosingElement();
		}
		return e;
	}

	/**
	 * Finds a potentially inherited member.
	 * @param parent the {@link ClassContainer} representing the parent
	 * @param name the name to search for
	 * @param descr the descriptor to search for, or null if it's not a method
	 * @param strict whether to perform lookup in strict mode (see {@link Target#strict()} for more info)
	 * @param inherited whether to match implicitly inherited fields (see {@link Find#inherited()} for more info)
	 * @param field whether the member being searched is a field
	 * @param options the {@link ProcessorOptions} to be used
	 * @return the desired member, if it exists
	 * @throws Reportable if an error occurs
	 * @since 0.9.2
	 */
	public static Element findMember(
		ClassContainer parent,
		String name,
		String descr,
		boolean strict,
		boolean inherited,
		boolean field,
		ProcessorOptions options
	) {
		if(parent.elem == null) {
			if(inherited) {
				throw ErrorReporter.untraceableInheritance(parent);
			} else {
				throw ErrorReporter.notFound("parent", MemberType.CLASS, parent.name);
			}
		}

		ClassContainer parentCursor = parent;
		while(true) {
			Element found = findMember0(parentCursor, name, descr, strict, field, !inherited, options.env);
			if(found == null) {
				if(!parentCursor.elem.getSuperclass().getKind().equals(TypeKind.DECLARED)) {
					throw ErrorReporter.notFound(
						"(possibly inherited)",
						field ? MemberType.FIELD : MemberType.METHOD,
						name,
						descr,
						parent.name
					);
				}

				parentCursor = ClassContainer.describe(
					(TypeElement) options.env.getTypeUtils().asElement(parentCursor.elem.getSuperclass()),
					options
				);

				continue;
			}

			if(isAccessibleFrom(found, parent.elem, options.env)) {
				return found;
			} else {
				throw ErrorReporter.untraceableInheritance(parentCursor, found, parent);
			}
		}
	}

	private static Element findMember0(
		ClassContainer parent,
		String name,
		String descr,
		boolean strict,
		boolean field,
		boolean throwOnNotFound, // if false just return null
		ProcessingEnvironment env
	) {
		// try to find by name
		List<Element> candidates = parent.elem.getEnclosedElements()
			.stream()
			.filter(e -> (field && e instanceof VariableElement) || e instanceof ExecutableElement)
			.filter(e -> e.getSimpleName().contentEquals(name))
			.collect(Collectors.toList());

		if(candidates.isEmpty()) {
			if(throwOnNotFound) {
				throw ErrorReporter.notFound(
					field ? MemberType.FIELD : MemberType.METHOD,
					name,
					parent.name
				);
			} else {
				return null;
			}
		}

		if(candidates.size() == 1 && (!strict || descr == null)) {
			return candidates.get(0);
		}

		if(descr == null) {
			throw ErrorReporter.ambiguousLookup(
				field ? MemberType.FIELD : MemberType.METHOD,
				candidates.size(),
				name,
				parent.name
			);
		} else {
			if(field) {
				// fields can verify the signature for extra safety
				// but there can only be 1 field with a given name
				if(!descriptorFromType(candidates.get(0).asType(), env).equals(descr)) {
					throw ErrorReporter.notFound(
						null,
						MemberType.FIELD,
						name,
						descr,
						parent.name
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
				throw ErrorReporter.notFound(
					null,
					MemberType.METHOD,
					name,
					descr,
					parent.name
				);
			}

			if(candidates.size() > 1) {
				throw ErrorReporter.ambiguousLookup(
					MemberType.METHOD,
					candidates.size(),
					name,
					parent.name
				);
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
	 * @throws Reportable if an error occurs
	 * @since 0.5.2
	 */
	public static ExecutableElement findSyntheticBridge(
		ExecutableElement method,
		ProcessingEnvironment env
	) {
		TypeElement parent = (TypeElement) method.getEnclosingElement();
		ExecutableElement overriding = findOverriddenMethod(parent, method, env);
		if(descriptorFromExecutableElement(overriding, env).equals(descriptorFromExecutableElement(method, env))) {
			throw ErrorReporter.notFound(
				"bridge",
				MemberType.METHOD,
				overriding.getSimpleName().toString(),
				null,
				parent.getQualifiedName().toString()
			);
		} else {
			return overriding;
		}
	}

	/**
	 * Utility method for finding out what type of proxy a field is.
	 * It will fail if the return type is not a known type of proxy.
	 * @param v the annotated {@link VariableElement}
	 * @param options the {@link ProcessorOptions}
	 * @return the {@link ProxyType} for the element
	 * @throws Reportable if it's not a known type
	 * @since 0.4.0
	 */
	public static ProxyType getProxyType(VariableElement v, ProcessorOptions options) {
		String returnTypeFQN = v.asType().toString();
		if(returnTypeFQN.equals(options.apiPackage + ".proxies.impl.FieldProxy")) {
			return ProxyType.FIELD;
		} else if(returnTypeFQN.equals(options.apiPackage + ".proxies.impl.MethodProxy")) {
			return ProxyType.METHOD;
		} else if(returnTypeFQN.equals(options.apiPackage + ".proxies.impl.TypeProxy")) {
			return ProxyType.TYPE;
		} else if(returnTypeFQN.equals(options.apiPackage + ".proxies.impl.PackageProxy")) {
			return ProxyType.PACKAGE;
		}

		throw ErrorReporter.notAProxy(v);
	}

	/**
	 * Checks whether a certain type identifier can be validated by the processor.
	 * @param name the name to validate
	 * @return true if it can be validated, false otherwise
	 * @since 0.7.0
	 */
	public static boolean shouldValidate(String name) {
		return SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name);
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
	 * @throws Reportable if something goes wrong
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
		int candidates = finderCandidates.size() + injectorCandidates.size();
		if(!finderCandidates.isEmpty() && !injectorCandidates.isEmpty()) {
			throw ErrorReporter.ambiguousOf(targetAnn.of(), candidates);
		} else if(finderCandidates.isEmpty() && injectorCandidates.isEmpty()) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
				String.format(
					"Found orphan @Target annotation on method %s.%s pointing at method %s, it will be ignored!",
					parent.getSimpleName(),
					target.getSimpleName(),
					targetAnn.of()
				)
			);
			return null;
		} else if(finderCandidates.isEmpty() && injectorCandidates.size() != 1) {
			throw ErrorReporter.ambiguousOf(targetAnn.of(), candidates);
		} else if(injectorCandidates.isEmpty() && finderCandidates.size() != 1) {
			throw ErrorReporter.ambiguousOf(targetAnn.of(), candidates);
		} else {
			if(injectorCandidates.size() == 1) {
				return injectorCandidates.get(0);
			} else {
				return finderCandidates.get(0);
			}
		}
	}
}
