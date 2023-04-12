package ftbsc.lll.processor.tools;

import com.squareup.javapoet.*;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.tools.containers.ClassContainer;
import ftbsc.lll.processor.tools.containers.FieldContainer;
import ftbsc.lll.processor.tools.containers.InjectorInfo;
import ftbsc.lll.processor.tools.containers.MethodContainer;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;
import ftbsc.lll.proxies.ProxyType;
import ftbsc.lll.proxies.impl.FieldProxy;
import ftbsc.lll.proxies.impl.MethodProxy;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
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
	 * Appends to a given {@link MethodSpec.Builder} definitions for a proxy.
	 * @param var the {@link VariableElement} representing the proxy
	 * @param stub the stub {@link ExecutableElement} if present or relevant, null otherwise
	 * @param t the {@link Target} relevant to this finder if present or relevant, null otherwise
	 * @param con the {@link MethodSpec.Builder} to append to
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @param mapper the {@link ObfuscationMapper} to use, may be null
	 * @since 0.5.0
	 */
	public static void appendMemberFinderDefinition(
		VariableElement var, ExecutableElement stub, Target t,
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
			target == null ? 0 : mapModifiers(target.getModifiers())
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
	 * Generates a {@link HashSet} of dummy overrides for every abstract method in a given class,
	 * represented as a {@link TypeElement}.
 	 * @param clazz the given class
	 * @return a {@link HashSet} containing the generated {@link MethodSpec}s
	 * @since 0.5.0
	 */
	public static HashSet<MethodSpec> generateDummies(TypeElement clazz) {
		HashSet<MethodSpec> specs = new HashSet<>();
		clazz
			.getEnclosedElements()
			.stream()
			.filter(e -> e instanceof ExecutableElement)
			.map(e -> (ExecutableElement) e)
			.forEach(e -> {
				if(e.getModifiers().contains(Modifier.ABSTRACT))
					specs.add(MethodSpec.overriding(e)
						.addStatement("throw new $T($S)", RuntimeException.class, "This is a stub and should not have been called")
						.build()
					);
			});
		return specs;
	}

	/**
	 * Generates the wrapper around a certain injector.
	 * @param inj the {@link InjectorInfo} carrying the information about the target injector
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @return the generated {@link MethodSpec} for the injector
	 * @since 0.6.0
	 */
	public static MethodSpec generateInjector(InjectorInfo inj, ProcessingEnvironment env) {
		MethodSpec.Builder injectBuilder = MethodSpec.methodBuilder("inject")
			.addModifiers(Modifier.PUBLIC)
			.returns(void.class)
			.addAnnotation(Override.class);

		int argumentCount = inj.injector.getParameters().size();

		if(argumentCount == 2) {
			injectBuilder
				.addParameter(ParameterSpec.builder(
						TypeName.get(env
							.getElementUtils()
							.getTypeElement("org.objectweb.asm.tree.ClassNode").asType()), "clazz")
					.build());
		}

		injectBuilder
			.addParameter(ParameterSpec.builder(
				TypeName.get(env
					.getElementUtils()
					.getTypeElement("org.objectweb.asm.tree.MethodNode").asType()), "main")
				.build());

		if(argumentCount == 2) injectBuilder.addStatement("super.$L(clazz, main)", inj.injector.getSimpleName());
		else injectBuilder.addStatement("super.$L(main)", inj.injector.getSimpleName());

		return injectBuilder.build();
	}
}
