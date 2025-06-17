package ftbsc.lll.processor.containers;

import com.squareup.javapoet.MethodSpec;
import ftbsc.lll.processor.ProcessorOptions;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.proxies.ProxyType;
import ftbsc.lll.proxies.impl.FieldProxy;
import ftbsc.lll.proxies.impl.MethodProxy;
import ftbsc.lll.proxies.impl.TypeProxy;

import javax.lang.model.element.*;

import static ftbsc.lll.processor.utils.ASTUtils.getProxyType;
import static ftbsc.lll.processor.utils.ASTUtils.mapModifiers;

/**
 * Carries information about a {@link Find}/{@link Target} combination.
 */
public class FinderInfo {
	/**
	 * The class annotated with {@link Patch} that contains the finder.
	 */
	public final TypeElement injectorClass;

	/**
	 * The {@link VariableElement} that is annotated.
	 */
	public final VariableElement proxy;

	/**
	 * The {@link ExecutableElement} the variable element is referring to (nullable).
	 */
	public final ExecutableElement targetStub;

	/**
	 * The {@link Target} annotation that points to this (nullable).
	 */
	public final Target targetAnn;

	/**
	 * Public constructor.
	 * @param injectorClass the class annotated with {@link Patch} that contains the finder
	 * @param proxy the {@link VariableElement} in question
	 * @param targetStub the {@link ExecutableElement} the variable element is referring to (nullable)
	 * @param targetAnn the {@link Target} annotation that points to this (nullable)
	 */
	public FinderInfo(
		TypeElement injectorClass,
		VariableElement proxy,
		ExecutableElement targetStub,
		Target targetAnn
	) {
		this.injectorClass = injectorClass;
		this.proxy = proxy;
		this.targetStub = targetStub;
		this.targetAnn = targetAnn;
	}

	/**
	 * Appends to a given {@link MethodSpec.Builder} the definitions for a proxy.
	 * @param methodBuilder the {@link MethodSpec.Builder} to append to
	 * @param local whether to initialize them as local variables
	 * @param options the {@link ProcessorOptions} to be used
	 */
	public void appendToMethodSpec(MethodSpec.Builder methodBuilder, boolean local, ProcessorOptions options) {
		ProxyType type = getProxyType(this.proxy);

		if(type == ProxyType.PACKAGE) return; // packages don't need init

		Find f = this.proxy.getAnnotation(Find.class);

		// types are initialized in a different way
		if(type == ProxyType.TYPE) {
			ClassContainer clazz = ClassContainer.findOrFallback(
				this.injectorClass,
				this.injectorClass.getAnnotation(Patch.class),
				f,
				options
			);

			if(local) {
				methodBuilder.addStatement(
					"$T $L = $T.from($S, 0, $L)",
					TypeProxy.class,
					this.proxy.getSimpleName().toString(),
					TypeProxy.class,
					clazz.data.nameMapped.replace('/', '.'), // use obf name, at runtime it will be obfuscated
					clazz.elem == null ? 0 : mapModifiers(clazz.elem.getModifiers())
				);
			} else {
				methodBuilder.addStatement(
					"super.$L = $T.from($S, 0, $L)",
					this.proxy.getSimpleName().toString(),
					TypeProxy.class,
					clazz.data.nameMapped.replace('/', '.'), // use obf name, at runtime it will be obfuscated
					clazz.elem == null ? 0 : mapModifiers(clazz.elem.getModifiers())
				);
			}

			return;
		}

		final boolean isMethod = type == ProxyType.METHOD;
		final String builderName = this.proxy.getSimpleName().toString() + "Builder";

		String descriptorObf, nameObf;
		ClassContainer parent;
		Element target;

		if(isMethod) {
			MethodContainer mc = MethodContainer.from(this.targetStub, this.targetAnn, f, options);
			descriptorObf = mc.descriptorObf;
			nameObf = mc.data.nameMapped;
			parent = mc.parent;
			target = mc.elem;
		} else {
			FieldContainer fc = FieldContainer.from(this.proxy, options);
			descriptorObf = fc.descriptorObf;
			nameObf = fc.data.nameMapped;
			parent = fc.parent;
			target = fc.elem;
		}

		// initialize builder
		methodBuilder.addStatement("$T $L = $T.builder($S)",
			isMethod ? MethodProxy.Builder.class : FieldProxy.Builder.class,
			builderName, //variable name is always unique by definition
			isMethod ? MethodProxy.class : FieldProxy.class,
			nameObf
		);

		// set parent
		methodBuilder.addStatement(
			"$L.setParent($S, $L)",
			builderName,
			parent.data.nameMapped.replace('/', '.'),
			parent.elem == null ? 0 : mapModifiers(parent.elem.getModifiers())
		);

		// set modifiers
		methodBuilder.addStatement(
			"$L.setModifiers($L)",
			builderName,
			target == null ? 0 : mapModifiers(target.getModifiers())
		);

		// set type(s)
		methodBuilder.addStatement(
			"$L.setDescriptor($S)",
			builderName,
			descriptorObf
		);

		// build and set
		if(local) {
			methodBuilder.addStatement(
				"$T $L = $L.build()",
				isMethod ? MethodProxy.class : FieldProxy.class,
				this.proxy.getSimpleName().toString(),
				builderName
			);
		} else {
			methodBuilder.addStatement(
				"super.$L = $L.build()",
				this.proxy.getSimpleName().toString(),
				builderName
			);
		}
	}
}
