package ftbsc.lll.processor.tools;

import com.squareup.javapoet.*;
import ftbsc.lll.tools.DescriptorBuilder;
import ftbsc.lll.proxies.MethodProxy;
import ftbsc.lll.proxies.FieldProxy;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.Function;

import static ftbsc.lll.processor.tools.ASTUtils.classArrayFromAnnotation;

/**
 * Collection of static utils that rely on JavaPoet to function.
 */
public class JavaPoetUtils {
	/**
	 * Builds a {@link MethodSpec} for a public method whose body simply returns a {@link String}.
	 * @param name the name of the method
	 * @param returnString the {@link String} to return
	 * @return the built {@link MethodSpec}
	 */
	public static MethodSpec buildStringReturnMethod(String name, String returnString) {
		return MethodSpec.methodBuilder(name)
			.addModifiers(Modifier.PUBLIC)
			.addAnnotation(Override.class)
			.returns(String.class)
			.addStatement("return $S", returnString)
			.build();
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
		if(type instanceof ClassName || type instanceof ParameterizedTypeName) {
			ClassName var = type instanceof ParameterizedTypeName ? ((ParameterizedTypeName) type).rawType : (ClassName) type;
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
	 * Builds a (partial, not including the return type) method descriptor from its parameters
	 * @param ann the annotation containing the class
	 * @param fun the annotation function returning the class
	 * @param elementUtils the {@link Elements} containing utils for the current processing environment
	 * @param <T> the type of the annotation carrying the information
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

	/**
	 * Adds to the given {@link MethodSpec.Builder} the given line of code,
	 * containing a call to a method of a {@link MethodProxy.Builder} or a
	 * {@link FieldProxy.Builder}.
	 * @param b the {@link MethodSpec.Builder}
	 * @param proxyBuilderName the name of the proxy builder
	 * @param proxyBuilderMethod the method to call
	 * @param t the {@link TypeMirror} to add
	 * @since 0.4.0
	 */
	public static void addTypeToProxyGenerator(MethodSpec.Builder b, String proxyBuilderName, String proxyBuilderMethod, TypeMirror t) {
		String insn = String.format("%s.%s", proxyBuilderName, proxyBuilderMethod);
		if(t.getKind().isPrimitive())
			b.addStatement(insn + "($T.class)", t);
		else {
			ArrayContainer arr = new ArrayContainer(t);
			TypeName type = TypeName.get(arr.innermostComponent);
			if(type instanceof ParameterizedTypeName)
				type = ((ParameterizedTypeName) type).rawType;
			b.addStatement(
				insn + "($S, $L)",
				type,
				arr.arrayLevel
			);
		}
	}
}
