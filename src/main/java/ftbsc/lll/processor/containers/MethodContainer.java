package ftbsc.lll.processor.containers;

import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.exceptions.TargetNotFoundException;
import ftbsc.lll.mapper.tools.MappingUtils;
import ftbsc.lll.mapper.tools.data.MethodData;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.ProcessorOptions;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import static ftbsc.lll.processor.utils.ASTUtils.*;

/**
 * Container for information about a method.
 * Used internally for efficiency reasons.
 * @since 0.5.0
 */
public class MethodContainer {
	/**
	 * The {@link MethodData} for the method represented by this container.
	 */
	public final MethodData data;

	/**
	 * The obfuscated descriptor of the field.
	 * If the mapper passed is null, this will be identical to the one inside
	 * {@link #data}.
	 */
	public final String descriptorObf;

	/**
	 * The {@link ClassContainer} representing the parent of this method.
	 */
	public final ClassContainer parent;

	/**
	 * The {@link ExecutableElement} corresponding to the method.
	 * May only be null intentionally i.e. when the method is
	 * a child of an anonymous class.
	 */
	public final ExecutableElement elem;

	/**
	 * Private constructor, called from
	 * {@link #from(ExecutableElement, Target, Find, ProcessorOptions)}.
	 * @param parent the {@link ClassContainer} representing the parent
	 * @param name the fully-qualified name of the target method
	 * @param descriptor the descriptor of the target method
	 * @param strict whether the matching should be strict (see {@link Target#strict()} for more info)
	 * @param bridge whether the "bridge" should be matched instead (see {@link Target#bridge()} for more info)
	 * @param options the {@link ProcessorOptions} to be used
	 */
	private MethodContainer(ClassContainer parent, String name, String descriptor, boolean strict, boolean bridge, ProcessorOptions options) {
		this.parent = parent;
		if(parent.elem == null) { //unverified
			if(descriptor == null)
				throw new AmbiguousDefinitionException("Cannot use name-based lookups for methods of unverifiable classes!");
			this.elem = null;
		} else {
			ExecutableElement tmp = (ExecutableElement) findMember(
				parent, name, descriptor, descriptor != null && strict,false, options.env
			);
			this.elem = bridge ? findSyntheticBridge((TypeElement) this.parent.elem, tmp, options.env) : tmp;
			name = this.elem.getSimpleName().toString();
			descriptor = descriptorFromExecutableElement(this.elem, options.env);
		}
		this.data = getMethodData(parent.data.name, name, descriptor, options.mapper);
		this.descriptorObf = options.mapper == null ? this.data.signature.descriptor
			: MappingUtils.mapMethodDescriptor(this.data.signature.descriptor, options.mapper, false);
	}

	/**
	 * Builds the {@link MethodContainer} corresponding to a stub annotated with {@link Target}.
	 * @param stub the {@link ExecutableElement} for the stub
	 * @param t the {@link Target} annotation relevant to this case
	 * @param f the {@link Find} annotation containing fallback data, may be null
	 * @param options the {@link ProcessorOptions} to be used
	 * @return the {@link MethodContainer} corresponding to the method
	 * @throws AmbiguousDefinitionException if it finds more than one candidate
	 * @throws TargetNotFoundException if it finds no valid candidate
	 * @since 0.3.0
	 */
	public static MethodContainer from(ExecutableElement stub, Target t, Find f, ProcessorOptions options) {
		//the parent always has a @Patch annotation
		Patch patchAnn = stub.getEnclosingElement().getAnnotation(Patch.class);
		ClassContainer parent = ClassContainer.findOrFallback(
			ClassContainer.from((TypeElement) stub.getEnclosingElement(), options), patchAnn, f, options
		);
		String name = !t.methodName().isEmpty()
			?	t.methodName() //name was specified in target
			: stub.getSimpleName().toString();
		String descriptor = t.strict()
			? descriptorFromExecutableElement(stub, options.env)
			: null;

		return new MethodContainer(parent, name, descriptor, t.strict(), t.bridge(), options);
	}
}
