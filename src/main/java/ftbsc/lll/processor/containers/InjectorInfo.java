package ftbsc.lll.processor.containers;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import ftbsc.lll.processor.reporting.ErrorReporter;
import ftbsc.lll.processor.annotations.Injector;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.ProcessorOptions;
import ftbsc.lll.processor.reporting.Reportable;
import ftbsc.lll.processor.utils.ASTUtils;
import ftbsc.lll.processor.utils.JavaPoetUtils;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.util.*;

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
	 * The output package.
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

		this.validateVisibility(options, injector, targetStub);

		this.target = MethodContainer.from(targetStub, targetAnn, null, options);
	}


	/**
	 * Validates the visibility in the target package of the given elements.
	 * Null elements are skipped and will NOT throw exceptions.
	 * @param options the {@link ProcessorOptions} to be used
	 * @param toValidate the elements to validate
	 * @throws Reportable if it is not visible
	 * @since 0.9.8
	 */
	public void validateVisibility(ProcessorOptions options, Element... toValidate) {
		for(Element cur : toValidate) {
			if(cur == null) {
				continue;
			}

			Element top = ASTUtils.getTopLevel(cur);
			boolean inDifferentPackage = this.outputPackage.equals(
				options.env.getElementUtils().getPackageOf(cur).toString()
			);

			do {
				Modifier curMod = ASTUtils.getVisibilityModifier(cur);
				if(
					Modifier.PUBLIC.equals(curMod)
						|| (Modifier.PROTECTED.equals(curMod) && !(cur instanceof TypeElement))
						|| (curMod == null && !inDifferentPackage)
				) {
					cur = cur.getEnclosingElement();
					continue;
				}

				throw ErrorReporter.notVisible(cur);
			} while(!cur.equals(top));
		}
	}

	/**
	 * Generates the wrapper around a certain injector.
	 * @param options the {@link ProcessorOptions} for this operation
	 * @return the generated {@link MethodSpec} for the injector
	 */
	public MethodSpec generateInjector(ProcessorOptions options) {
		TypeMirror classNode = options.env
			.getElementUtils()
			.getTypeElement(options.asmPackage + ".ClassNode").asType();

		TypeMirror methodNode = options.env
			.getElementUtils()
			.getTypeElement(options.asmPackage + ".MethodNode").asType();

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
			else if(finderMap.containsKey(param)) sb.append(JavaPoetUtils.escapeString(param.getSimpleName().toString())).append(",");
			else throw ErrorReporter.orphan(param);
		}

		injectBuilder.addStatement(
			sb.deleteCharAt(sb.length() - 1).append(")").toString(),
			this.injector.getSimpleName()
		);

		return injectBuilder.build();
	}
}
