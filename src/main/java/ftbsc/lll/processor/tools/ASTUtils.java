package ftbsc.lll.processor.tools;

import com.squareup.javapoet.*;
import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.exceptions.MappingNotFoundException;
import ftbsc.lll.exceptions.TargetNotFoundException;
import ftbsc.lll.processor.annotations.FindField;
import ftbsc.lll.processor.annotations.FindMethod;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ftbsc.lll.processor.tools.JavaPoetUtils.descriptorFromExecutableElement;
import static ftbsc.lll.processor.tools.JavaPoetUtils.methodDescriptorFromParams;

/**
 * Collection of AST-related static utils that didn't really fit into the main class.
 */
public class ASTUtils {
	/**
	 * Finds, among the methods of a class cl, the one annotated with ann, and tries to build
	 * a {@link ExecutableElement} from it.
	 * @param cl the {@link ExecutableElement} for the class containing the desired method
	 * @param ann the {@link Class} corresponding to the desired annotation
	 * @return a {@link List} of {@link MethodSpec}s annotated with the given annotation
	 * @since 0.2.0
	 */
	public static List<ExecutableElement> findAnnotatedMethods(TypeElement cl, Class<? extends Annotation> ann) {
		return cl.getEnclosedElements()
			.stream()
			.filter(e -> e.getAnnotationsByType(ann).length != 0)
			.map(e -> (ExecutableElement) e)
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
	 * Calculates the array nesting level for a {@link TypeMirror}.
	 * @param t the type mirror to get it for
	 * @return the array nesting level
	 * @since 0.3.0
	 */
	public static int getArrayLevel(TypeMirror t) {
		int arrayLevel = 0;
		while(t.getKind() == TypeKind.ARRAY) {
			t = ((ArrayType) t).getComponentType();
			arrayLevel++;
		}
		return arrayLevel;
	}

	/**
	 * Calculates the array nesting level for a {@link TypeMirror}.
	 * @param t the type mirror to get it for
	 * @return the array nesting level
	 * @since 0.3.0
	 */
	public static TypeMirror getInnermostComponentType(TypeMirror t) {
		while(t.getKind() == TypeKind.ARRAY)
			t = ((ArrayType) t).getComponentType();
		return t;
	}

	/**
	 * Safely extracts a {@link Class} from an annotation and gets its fully qualified name.
	 * @param ann the annotation containing the class
	 * @param fun the annotation function returning the class
	 * @return the fully qualified name of the given class
	 * @since 0.3.0
	 */
	public static <T extends Annotation> String getClassFullyQualifiedName(T ann, Function<T, Class<?>> fun) {
		try {
			return fun.apply(ann).getCanonicalName();
		} catch(MirroredTypeException e) {
			return e.getTypeMirror().toString();
		}
	}

	/**
	 * Safely extracts a {@link Class} array from an annotation.
	 * @param ann the annotation containing the class
	 * @param fun the annotation function returning the class
	 * @param elementUtils the element utils corresponding to the {@link ProcessingEnvironment}
	 * @return a list of {@link TypeMirror}s representing the classes
	 * @since 0.3.0
	 */
	public static <T extends Annotation> List<TypeMirror> classArrayFromAnnotation(T ann, Function<T, Class<?>[]> fun, Elements elementUtils) {
		List<TypeMirror> params = new ArrayList<>();
		try {
			params.addAll(Arrays.stream(fun.apply(ann))
				.map(Class::getCanonicalName)
				.map(fqn -> elementUtils.getTypeElement(fqn).asType())
				.collect(Collectors.toList()));
		} catch(MirroredTypesException e) {
			params.addAll(e.getTypeMirrors());
		}
		return params;
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
	 * Finds the class name and maps it to the correct format.
	 * @param patchAnn  the {@link Patch} annotation containing target class info
	 * @param finderAnn an annotation containing metadata about the target, may be null
	 * @param parentFun the function to get the parent from the finderAnn
	 * @return the fully qualified class name
	 * @since 0.3.0
	 */
	private static <T extends Annotation> String findClassName(Patch patchAnn, T finderAnn, Function<T, Class<?>> parentFun) {
		String fullyQualifiedName;
		if(finderAnn != null) {
			fullyQualifiedName = getClassFullyQualifiedName(finderAnn, parentFun);
			if(!fullyQualifiedName.equals("java.lang.Object"))
				return findClassName(fullyQualifiedName, null);
		}
		fullyQualifiedName = getClassFullyQualifiedName(patchAnn, Patch::value);
		return findClassName(fullyQualifiedName, null);
	}

	/**
	 * Finds the member name and maps it to the correct format.
	 * @param parentFQN the already mapped FQN of the parent class
	 * @param memberName the name of the member
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
	 * Finds a method given name, container and descriptor.
	 * @param parentFQN the fully qualified name of the parent class of the method
	 * @param name the name to search for
	 * @param descr the descriptor to search for
	 * @param strict whether the search should be strict (see {@link Target#strict()} for more info)
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @return the desired method, if it exists
	 * @throws AmbiguousDefinitionException if it finds more than one candidate
	 * @throws TargetNotFoundException if it finds no valid candidate
	 * @since 0.3.0
	 */
	private static ExecutableElement findMethod(String parentFQN, String name, String descr, boolean strict, ProcessingEnvironment env) {
		TypeElement parent = env.getElementUtils().getTypeElement(parentFQN);
		if(parent == null)
			throw new AmbiguousDefinitionException(String.format("Could not find parent class %s!", parentFQN));

		//try to find by name
		List<ExecutableElement> candidates = parent.getEnclosedElements()
			.stream()
			.filter(e -> e instanceof ExecutableElement)
			.map(e -> (ExecutableElement) e)
			.filter(e -> e.getSimpleName().contentEquals(name))
			.collect(Collectors.toList());
		if(candidates.size() == 0)
			throw new TargetNotFoundException(String.format("%s %s", name, descr));
		if(candidates.size() == 1 && !strict)
			return candidates.get(0);
		if(descr == null) {
			throw new AmbiguousDefinitionException(
				String.format("Found %d methods named %s in class %s!", candidates.size(), name, parentFQN)
			);
		} else {
			candidates = candidates.stream()
				.filter(strict
					? c -> descr.equals(descriptorFromExecutableElement(c))
					: c -> descr.split("\\)")[0].equalsIgnoreCase(descriptorFromExecutableElement(c).split("\\)")[0])
				).collect(Collectors.toList());
			if(candidates.size() == 0)
				throw new TargetNotFoundException(String.format("%s %s", name, descr));
			if(candidates.size() > 1)
				throw new AmbiguousDefinitionException(
					String.format("Found %d methods named %s in class %s!", candidates.size(), name, parentFQN)
				);
			return candidates.get(0);
		}
	}

	/**
	 * Finds the real class member (field or method) corresponding to a stub annotated with
	 * {@link Target} or {@link FindMethod} or {@link FindField}.
	 * @param stub the {@link ExecutableElement} for the stub
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @return the {@link Element} corresponding to the method or field
	 * @throws AmbiguousDefinitionException if it finds more than one candidate
	 * @throws TargetNotFoundException if it finds no valid candidate
	 * @since 0.3.0
	 */
	public static Element findMemberFromStub(ExecutableElement stub, ProcessingEnvironment env) {
		//the parent always has a @Patch annotation
		Patch patchAnn = stub.getEnclosingElement().getAnnotation(Patch.class);
		//there should ever only be one of these
		Target targetAnn = stub.getAnnotation(Target.class); //if this is null strict mode is always disabled
		FindMethod findMethodAnn = stub.getAnnotation(FindMethod.class); //this may be null, it means no fallback info
		FindField findFieldAnn = stub.getAnnotation(FindField.class);
		String parentFQN, memberName;
		if(findFieldAnn == null) { //methods
			parentFQN = findClassName(patchAnn, findMethodAnn, FindMethod::parent);
			String methodDescriptor =
				findMethodAnn != null
					? methodDescriptorFromParams(findMethodAnn, FindMethod::params, env.getElementUtils())
					: descriptorFromExecutableElement(stub);
			memberName =
				findMethodAnn != null && !findMethodAnn.name().equals("")
					? findMethodAnn.name()
					: stub.getSimpleName().toString();
			return findMethod(
				parentFQN,
				memberName,
				methodDescriptor,
				targetAnn != null && targetAnn.strict(),
				env
			);
		} else { //fields
			parentFQN = findClassName(patchAnn, findFieldAnn, FindField::parent);
			memberName = findFieldAnn.name().equals("")
				? stub.getSimpleName().toString()
				: findFieldAnn.name();
			TypeElement parent = env.getElementUtils().getTypeElement(parentFQN);
			List<VariableElement> candidates =
				parent.getEnclosedElements()
					.stream()
					.filter(f -> f instanceof VariableElement)
					.filter(f -> f.getSimpleName().contentEquals(memberName))
					.map(f -> (VariableElement) f)
					.collect(Collectors.toList());
			if(candidates.size() == 0)
				throw new TargetNotFoundException(stub.getSimpleName().toString());
			else return candidates.get(0); //there can only ever be one
		}
	}
}
