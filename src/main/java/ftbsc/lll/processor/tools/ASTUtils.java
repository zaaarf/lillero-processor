package ftbsc.lll.processor.tools;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import ftbsc.lll.tools.DescriptorBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Collection of static utils that didn't really fit into the main class.
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
	 * Builds a type descriptor from the given {@link TypeName}.
	 * @param type the {@link TypeName} representing the desired type
	 * @return a {@link String} containing the relevant descriptor
	 */
	public static String descriptorFromType(TypeName type) {
		StringBuilder desc = new StringBuilder();
		//add array brackets
		while(type instanceof ArrayTypeName) {
			desc.append("[");
			type = ((ArrayTypeName) type).componentType;
		}
		if(type instanceof ClassName) {
			ClassName var = (ClassName) type;
			desc.append(DescriptorBuilder.nameToDescriptor(var.canonicalName(), 0));
		} else {
			if(TypeName.BOOLEAN.equals(type))
				desc.append("Z");
			else if(TypeName.CHAR.equals(type))
				desc.append("C");
			else if(TypeName.BYTE.equals(type))
				desc.append("B");
			else if(TypeName.SHORT.equals(type))
				desc.append("S");
			else if(TypeName.INT.equals(type))
				desc.append("I");
			else if(TypeName.FLOAT.equals(type))
				desc.append("F");
			else if(TypeName.LONG.equals(type))
				desc.append("J");
			else if(TypeName.DOUBLE.equals(type))
				desc.append("D");
			else if(TypeName.VOID.equals(type))
				desc.append("V");
		}
		return desc.toString();
	}

	/**
	 * Builds a type descriptor from the given {@link TypeMirror}.
	 * @param t the {@link TypeMirror} representing the desired type
	 * @return a {@link String} containing the relevant descriptor
	 */
	public static String descriptorFromType(TypeMirror t) {
		return descriptorFromType(TypeName.get(t));
	}

	/**
	 * Builds a method descriptor from the given {@link ExecutableElement}.
	 * @param m the {@link ExecutableElement} for the method
	 * @return a {@link String} containing the relevant descriptor
	 */
	public static String descriptorFromExecutableElement(ExecutableElement m) {
		StringBuilder methodSignature = new StringBuilder();
		methodSignature.append("(");
		m.getParameters().forEach(p -> methodSignature.append(descriptorFromType(p.asType())));
		methodSignature.append(")");
		methodSignature.append(descriptorFromType(m.getReturnType()));
		return methodSignature.toString();
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
	 * Builds a (partial, not including the return type) method descriptor from its parameters
	 * @param ann the annotation containing the class
	 * @param fun the annotation function returning the class
	 * @return the method descriptor
	 */
	public static <T extends Annotation> String methodDescriptorFromParams(T ann, Function<T, Class<?>[]> fun, Elements elementUtils) {
		List<TypeMirror> mirrors = classArrayFromAnnotation(ann, fun, elementUtils);
		StringBuilder sb = new StringBuilder("(");
		for(TypeMirror t : mirrors)
			sb.append(descriptorFromType(t));
		sb.append(")");
		return sb.toString();
	}
}
