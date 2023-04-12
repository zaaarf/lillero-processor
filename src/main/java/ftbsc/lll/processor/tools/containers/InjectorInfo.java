package ftbsc.lll.processor.tools.containers;

import ftbsc.lll.processor.annotations.Injector;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;

/**
 * Container for information about a class that is to be generated.
 */
public class InjectorInfo {
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
	 * The {@link MethodContainer} corresponding to the target method.
	 */
	public final MethodContainer target;

	/**
	 * Public constructor.
	 * @param injector the injector {@link ExecutableElement}
	 * @param targetStub the target {@link ExecutableElement}
	 * @param targetAnn the relevant {@link Target} annotation
	 * @param env the {@link ProcessingEnvironment} to be used to locate the class
	 * @param mapper the {@link ObfuscationMapper} to be used, may be null
	 */
	public InjectorInfo(ExecutableElement injector, ExecutableElement targetStub, Target targetAnn, ProcessingEnvironment env, ObfuscationMapper mapper) {
		this.injector = injector;
		this.targetStub = targetStub;
		this.reason = injector.getAnnotation(Injector.class).reason();
		this.target = MethodContainer.from(targetStub, targetAnn, null, env, mapper);
	}
}