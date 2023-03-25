package ftbsc.lll.processor.tools;

import com.squareup.javapoet.*;
import ftbsc.lll.processor.LilleroProcessor;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.tools.containers.ArrayContainer;
import ftbsc.lll.processor.tools.containers.ClassContainer;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;
import ftbsc.lll.proxies.ProxyType;
import ftbsc.lll.tools.DescriptorBuilder;
import ftbsc.lll.proxies.impl.MethodProxy;
import ftbsc.lll.proxies.impl.FieldProxy;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.Function;

import static ftbsc.lll.processor.tools.ASTUtils.*;
import static ftbsc.lll.processor.tools.ASTUtils.mapModifiers;

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
		if(t.getKind().isPrimitive() || t.getKind() == TypeKind.VOID)
			b.addStatement(insn + "($T.class)", t);
		else {
			ArrayContainer arr = new ArrayContainer(t);
			TypeName type = TypeName.get(arr.innermostComponent);
			if(type instanceof ParameterizedTypeName)
				type = ((ParameterizedTypeName) type).rawType;
			b.addStatement(
				insn + "($S, $L)",
				type.toString(),
				arr.arrayLevel
			);
		}
	}

	/**
	 * Appends to a given {@link MethodSpec.Builder} definitions for a proxy.
	 * @param fallback the {@link ClassContainer} to fall back on
	 * @param var the {@link VariableElement} representing the proxy
	 * @param stub the stub {@link ExecutableElement} if present or relevant, null otherwise
	 * @param con the {@link MethodSpec.Builder} to append to
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @param mapper the {@link ObfuscationMapper} to use, may be null
	 * @since 0.5.0
	 */
	public static void appendMemberFinderDefinition(
		ClassContainer fallback, VariableElement var, ExecutableElement stub, MethodSpec.Builder con, ProcessingEnvironment env, ObfuscationMapper mapper) {
		ProxyType type = getProxyType(var);
		if(type != ProxyType.METHOD && type != ProxyType.FIELD)
			return; //this method is irrelevant to everyoen else

		//we need this stuff
		Find f = var.getAnnotation(Find.class);
		ClassContainer parent = findClassOrFallback(fallback, f, env, mapper);
		final boolean isMethod = type == ProxyType.METHOD;
		final String builderName = var.getSimpleName().toString() + "Builder";

		String name, nameObf;
		Element target;

		if(isMethod) {
			ExecutableElement executableTarget;
			if(f.name().equals("")) //find and validate from stub
				executableTarget = findMethodFromStub(stub, env);
			else { //find and validate by name alone
				if(LilleroProcessor.badPracticeWarnings) //warn user that he is doing bad stuff
					env.getMessager().printMessage(Diagnostic.Kind.WARNING,
						String.format("Matching method %s by name, this is bad practice and may lead to unexpected behaviour. Use @Target stubs instead!", f.name()));
				executableTarget = (ExecutableElement) findMember(parent.fqn, f.name(), null, false, false, env);
			}
			name = executableTarget.getSimpleName().toString();
			nameObf = findMemberName(parent.fqnObf, name, descriptorFromExecutableElement(executableTarget), mapper);
			target = executableTarget;
		} else {
			//find and validate target
			name = f.name().equals("") ? var.getSimpleName().toString() : f.name();
			target = findMember(parent.fqn, name, null, false, true, env);
			nameObf = findMemberName(parent.fqnObf, name, null, mapper);
		}

		//initialize builder
		con.addStatement("$T $L = $T.builder($S)",
			isMethod ? MethodProxy.Builder.class : FieldProxy.Builder.class,
			builderName, //variable name is always unique by definition
			isMethod ? MethodProxy.class : FieldProxy.class,
			nameObf
		);

		//set parent
		con.addStatement(
			"$L.setParent($S, $L)",
			builderName,
			parent.fqnObf,
			mapModifiers(parent.elem.getModifiers())
		);

		//set modifiers
		con.addStatement(
			"$L.setModifiers($L)",
			builderName,
			mapModifiers(target.getModifiers())
		);

		if(isMethod) { //set parameters and return type
			ExecutableElement executableTarget = (ExecutableElement) target;
			for(VariableElement p : executableTarget.getParameters())
				addTypeToProxyGenerator(con,	builderName, "addParameter", p.asType());
			addTypeToProxyGenerator(con, builderName, "setReturnType", executableTarget.getReturnType());
		} else //set type
			addTypeToProxyGenerator(con,builderName, "setType", target.asType());

		//build and set
		con.addStatement(
			"super.$L = $L.build()",
			var.getSimpleName().toString(),
			builderName
		);
	}
}
