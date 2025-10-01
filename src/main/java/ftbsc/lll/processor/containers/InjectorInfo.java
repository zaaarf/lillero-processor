package ftbsc.lll.processor.containers;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import ftbsc.lll.exceptions.OrphanElementException;
import ftbsc.lll.processor.annotations.Injector;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.ProcessorOptions;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for information about a class that is to be generated.
 */
public class InjectorInfo {
	/**
	 * The generated name of this class.
	 */
	public final String name;

	/**
	 * The {@link ExecutableElement} corresponding to the injector method.
	 */
	public final ExecutableElement injector;

	/**
	 * The {@link ExecutableElement} corresponding to the target method stub.
	 */
	public final ExecutableElement targetStub;

	/**
	 * The reason for the injection.
	 */
	public final String reason;

	/**
	 * The output packages.
	 */
	public final String outputPackage;

	/**
	 * The {@link MethodContainer} corresponding to the target method.
	 */
	public final MethodContainer target;

	/**
	 * Finders which are registered as parameters.
	 */
	public final List<FinderInfo> finderParams = new ArrayList<>();

	/**
	 * Public constructor.
	 * @param name the generated name
	 * @param injector the injector {@link ExecutableElement}
	 * @param targetStub the target {@link ExecutableElement}
	 * @param targetAnn the relevant {@link Target} annotation
	 * @param options the {@link ProcessorOptions} to be used
	 */
	public InjectorInfo(
		String name,
		ExecutableElement injector,
		ExecutableElement targetStub,
		Target targetAnn,
		ProcessorOptions options
	) {
		this.name = name;
		this.injector = injector;
		this.targetStub = targetStub;
		this.reason = injector.getAnnotation(Injector.class).reason();

		String localPkgOverride = injector.getAnnotation(Injector.class).outputPackage();
		if(!localPkgOverride.equals(Injector.DEFAULT_OUTPUT_PACKAGE)) {
			// local override
			this.outputPackage = localPkgOverride;
		} else if(options.outputPackage != null) {
			// environment level override
			this.outputPackage = options.outputPackage;
		} else {
			// fall back on current package
			this.outputPackage = options.env.getElementUtils().getPackageOf(injector).toString();
		}

		this.target = MethodContainer.from(targetStub, targetAnn, null, options);
	}

	/**
	 * Generates the wrapper around a certain injector.
	 * @param options the {@link ProcessorOptions} for this operation
	 * @return the generated {@link MethodSpec} for the injector
	 */
	public MethodSpec generateInjector(ProcessorOptions options) {
		TypeMirror classNode = options.env
			.getElementUtils()
			.getTypeElement("org.objectweb.asm.tree.ClassNode").asType();

		TypeMirror methodNode = options.env
			.getElementUtils()
			.getTypeElement("org.objectweb.asm.tree.MethodNode").asType();

		MethodSpec.Builder injectBuilder = MethodSpec.methodBuilder("inject")
			.addModifiers(Modifier.PUBLIC)
			.returns(void.class)
			.addAnnotation(Override.class)
			.addParameter(ParameterSpec.builder(TypeName.get(classNode), "clazz").build())
			.addParameter(ParameterSpec.builder(TypeName.get(methodNode), "method").build());

		Map<VariableElement, FinderInfo> finderMap = new HashMap<>();
		for(FinderInfo info : this.finderParams) {
			finderMap.put(info.proxy, info);
			info.appendToMethodSpec(injectBuilder, true, options);
		}

		StringBuilder sb = new StringBuilder("super.$L(");
		for(VariableElement param : this.injector.getParameters()) {
			if(param.asType().equals(classNode)) sb.append("clazz,");
			else if(param.asType().equals(methodNode)) sb.append("method,");
			else if(finderMap.containsKey(param)) sb.append(param.getSimpleName().toString()).append(",");
			else throw new OrphanElementException(param);
		}

		injectBuilder.addStatement(
			sb.deleteCharAt(sb.length() - 1).append(")").toString(),
			this.injector.getSimpleName()
		);

		return injectBuilder.build();
	}
}
