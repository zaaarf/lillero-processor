package ftbsc.lll.processor.tools;

import com.squareup.javapoet.*;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.tools.containers.ClassContainer;
import ftbsc.lll.processor.tools.containers.FieldContainer;
import ftbsc.lll.processor.tools.containers.MethodContainer;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;
import ftbsc.lll.proxies.ProxyType;
import ftbsc.lll.proxies.impl.FieldProxy;
import ftbsc.lll.proxies.impl.MethodProxy;
import ftbsc.lll.tools.DescriptorBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.HashSet;

import static ftbsc.lll.processor.tools.ASTUtils.getProxyType;
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
	 * Appends to a given {@link MethodSpec.Builder} definitions for a proxy.
	 * @param fallback the {@link ClassContainer} to fall back on
	 * @param var the {@link VariableElement} representing the proxy
	 * @param stub the stub {@link ExecutableElement} if present or relevant, null otherwise
	 * @param t the {@link Target} relevant to this finder if present or relevant, null otherwise
	 * @param con the {@link MethodSpec.Builder} to append to
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @param mapper the {@link ObfuscationMapper} to use, may be null
	 * @since 0.5.0
	 */
	public static void appendMemberFinderDefinition(
		ClassContainer fallback, VariableElement var, ExecutableElement stub, Target t,
		MethodSpec.Builder con, ProcessingEnvironment env, ObfuscationMapper mapper) {
		ProxyType type = getProxyType(var);
		if(type != ProxyType.METHOD && type != ProxyType.FIELD)
			return; //this method is irrelevant to everyone else

		//we need this stuff
		Find f = var.getAnnotation(Find.class);
		final boolean isMethod = type == ProxyType.METHOD;
		final String builderName = var.getSimpleName().toString() + "Builder";

		String descriptorObf, nameObf;
		ClassContainer parent;
		Element target;

		if(isMethod) {
			MethodContainer mc = MethodContainer.from(stub, t, f, env, mapper);
			descriptorObf = mc.descriptorObf;
			nameObf = mc.nameObf;
			parent = mc.parent;
			target = mc.elem;
		} else {
			FieldContainer fc = FieldContainer.from(var, env, mapper);
			descriptorObf = fc.descriptorObf;
			nameObf = fc.nameObf;
			parent = fc.parent;
			target = fc.elem;
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
			parent.elem == null ? 0 : mapModifiers(parent.elem.getModifiers())
		);

		//set modifiers
		con.addStatement(
			"$L.setModifiers($L)",
			builderName,
			target == null ? 0 :mapModifiers(target.getModifiers())
		);

		//set type(s)
		con.addStatement(
			"$L.setDescriptor($S)",
			builderName,
			descriptorObf
		);

		//build and set
		con.addStatement(
			"super.$L = $L.build()",
			var.getSimpleName().toString(),
			builderName
		);
	}

	/**
	 * Generates a {@link HashSet} of dummy overrides given a {@link Collection} stubs.
 	 * @param dummies the stubs
	 * @return the generated {@link HashSet}
	 * @since 0.5.0
	 */
	public static HashSet<MethodSpec> generateDummies(Collection<ExecutableElement> dummies) {
		HashSet<MethodSpec> specs = new HashSet<>();
		for(ExecutableElement d : dummies)
			if(d.getModifiers().contains(Modifier.ABSTRACT))
				specs.add(MethodSpec.overriding(d)
					.addStatement("throw new $T($S)", RuntimeException.class, "This is a stub and should not have been called")
					.build()
				);
		return specs;
	}
}
