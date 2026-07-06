package ftbsc.lll.processor.containers;

import ftbsc.lll.processor.reporting.ErrorReporter;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.ProcessorOptions;
import ftbsc.lll.processor.reporting.MemberType;
import ftbsc.lll.processor.reporting.Reportable;

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
	 * The name of the method.
	 */
	public final String name;

	/**
	 * The descriptor of the method.
	 */
	public final String descriptor;

	/**
	 * The mapped name of the method.
	 * Will be identical to {@link #name} if no mappings were given.
	 */
	public final String nameMapped;

	/**
	 * The mapped descriptor of the method.
	 * Will be identical to {@link #descriptor} if no mappings were given.
	 */
	public final String descriptorMapped;

	/**
	 * The {@link ClassContainer} representing the parent of this method.
	 */
	public final ClassContainer parent;

	/**
	 * The {@link ExecutableElement} corresponding to the method.
	 * May only be null intentionally i.e. when the method is
	 * a child of an anonymous class or for {@link Target#unchecked()}.
	 */
	public final ExecutableElement elem;

	/**
	 * Private constructor, called from
	 * {@link #from(ExecutableElement, Target, Find, ProcessorOptions)}.
	 * @param parent the {@link ClassContainer} representing the parent
	 * @param name the fully-qualified name of the target method
	 * @param descriptor the descriptor of the target method
	 * @param unchecked whether the matching should be unchecked (see {@link Target#unchecked()} for more info)
	 * @param strict whether the matching should be strict (see {@link Target#strict()} for more info)
	 * @param inherited whether to match implicitly inherited methods (see {@link Find#inherited()} for more info)
	 * @param bridge whether the "bridge" should be matched instead (see {@link Target#bridge()} for more info)
	 * @param options the {@link ProcessorOptions} to be used
	 */
	private MethodContainer(
		ClassContainer parent,
		String name,
		String descriptor,
		boolean unchecked,
		boolean strict,
		boolean bridge,
		boolean inherited,
		ProcessorOptions options
	) {
		this.parent = parent;
		if(parent.elem == null) { // unverified
			if(unchecked || strict) {
				this.elem = null;
			} else {
				throw ErrorReporter.badNameBasedLookup(MemberType.METHOD);
			}
		} else if(unchecked) {
			this.elem = null;
		} else {
			ExecutableElement tmp = (ExecutableElement) findMember(parent, name, descriptor, strict, inherited, false, options);
			if(bridge) {
				this.elem = findSyntheticBridge(tmp, options.env);
			} else this.elem = tmp;

			name = this.elem.getSimpleName().toString();
			if(strict) {
				descriptor = descriptorFromExecutableElement(this.elem, options.env);
			}
		}

		this.name = name;
		this.descriptor = descriptor;

		// some mapping formats omit methods if they are overriding a parent's method
		// if the mapper does not have this method, try to look up the top parent
		// since there is no real drawback in being slightly wasteful here
		if(
			this.parent.elem != null
				&& !options.mapper.hasMethod(parent.name, name, descriptor)
				&& this.elem != null
		) {
			ExecutableElement top = findOverriddenMethod(this.parent.elem, this.elem, options.env);
			this.nameMapped = options.mapper.mapMethodName(
				internalNameFromType(top.getEnclosingElement().asType(), options.env),
				top.getSimpleName().toString(),
				descriptorFromExecutableElement(top, options.env)
			);
		} else {
			this.nameMapped = options.mapper.mapMethodName(parent.name, name, descriptor);
		}

		this.descriptorMapped = options.mapper.mapDescriptor(this.descriptor);
	}

	/**
	 * Builds the {@link MethodContainer} corresponding to a stub annotated with {@link Target}.
	 * @param stub the {@link ExecutableElement} for the stub
	 * @param t the {@link Target} annotation relevant to this case
	 * @param f the {@link Find} annotation containing fallback data, may be null
	 * @param opts the {@link ProcessorOptions} to be used
	 * @return the {@link MethodContainer} corresponding to the method
	 * @throws Reportable if something goes wrong
	 * @since 0.3.0
	 */
	public static MethodContainer from(
		ExecutableElement stub,
		Target t,
		Find f,
		ProcessorOptions opts
	) {
		// the parent always has a @Patch annotation
		Patch p = stub.getEnclosingElement().getAnnotation(Patch.class);
		ClassContainer parent = ClassContainer.findOrFallback((TypeElement) stub.getEnclosingElement(), p, f, opts);
		String name = !t.methodName().isEmpty()
			?	t.methodName() // name was specified in target
			: stub.getSimpleName().toString();
		String descriptor = descriptorFromExecutableElement(stub, opts.env);

		return new MethodContainer(
			parent,
			name,
			descriptor,
			t.unchecked(),
			t.strict(),
			t.bridge(),
			f != null && f.inherited(),
			opts
		);
	}
}
