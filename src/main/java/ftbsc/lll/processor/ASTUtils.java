package ftbsc.lll.processor;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import ftbsc.lll.tools.DescriptorBuilder;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;

/**
 * Collection of static utils that didn't really fit into the main class.
 */
public class ASTUtils {
	/**
	 * Finds, among the methods of a class cl, the one annotated with ann, and tries to build
	 * a {@link ExecutableElement} from it.
	 * In case of multiple occurrences, only the first one is returned.
	 * No check existance check is performed within the method.
	 * @param cl the {@link ExecutableElement} for the class containing the desired method
	 * @param ann the {@link Class} corresponding to the desired annotation
	 * @return the {@link MethodSpec} representing the desired method
	 */
	@SuppressWarnings("OptionalGetWithoutIsPresent")
	public static ExecutableElement findAnnotatedMethod(TypeElement cl, Class<? extends Annotation> ann) {
		return (ExecutableElement) cl.getEnclosedElements()
			.stream()
			.filter(e -> e.getAnnotation(ann) != null)
			.findFirst()
			.get(); //will never be null so can ignore warning
	}

	/**
	 * Builds a type descriptor from the given {@link TypeMirror}
	 * @param t the {@link TypeMirror} representing the desired type
	 * @return a {@link String} containing the relevant descriptor
	 */
	public static String descriptorFromType(TypeMirror t) {
		TypeName type = TypeName.get(t);
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
	 * Builds a method descriptor from the given {@link ExecutableElement}.
	 * @param m the {@link ExecutableElement} for the method
	 * @return a {@link String} containing the relevant descriptor
	 */
	public static String descriptorFromMethodSpec(ExecutableElement m) {
		StringBuilder methodSignature = new StringBuilder();
		methodSignature.append("(");
		m.getParameters().forEach(p -> methodSignature.append(descriptorFromType(p.asType())));
		methodSignature.append(")");
		methodSignature.append(descriptorFromType(m.getReturnType()));
		return methodSignature.toString();
	}
}
