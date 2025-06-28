package ftbsc.lll.processor.containers;

import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.exceptions.TargetNotFoundException;
import ftbsc.lll.mapper.data.ClassData;
import ftbsc.lll.mapper.utils.MappingUtils;
import ftbsc.lll.mapper.data.MethodData;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Overridden;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.ProcessorOptions;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;

import java.util.List;
import java.util.stream.Collectors;

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
	 * @param bridge whether the "bridge" should be matched instead (see {@link Target#bridge()} for more info)
	 * @param overriddenStub a stub of the method this is supposedly overriding, may be null
	 * @param options the {@link ProcessorOptions} to be used
	 */
	private MethodContainer(
		ClassContainer parent,
		String name,
		String descriptor,
		boolean unchecked,
		boolean strict,
		boolean bridge,
		ExecutableElement overriddenStub,
		ProcessorOptions options
	) {
		this.parent = parent;
		if(parent.elem == null) { // unverified
			if(unchecked || strict) {
				this.elem = null;
			} else {
				throw new AmbiguousDefinitionException(
					"Cannot use name-based lookups for methods of unverifiable classes!"
				);
			}
		} else if(unchecked) {
			this.elem = null;
		} else {
			ExecutableElement tmp = (ExecutableElement) findMember(
				parent, name, descriptor, strict, false, options.env
			);

			if(bridge) {
				this.elem = findSyntheticBridge(tmp, options.env);
			} else this.elem = tmp;

			name = this.elem.getSimpleName().toString();
			if(strict) {
				descriptor = descriptorFromExecutableElement(this.elem, options.env);
			}
		}

		boolean[] lookupOutcome = { true };
		MethodData baseData = getMethodData(parent.data.name, name, descriptor, options.mapper, lookupOutcome);

		// some mapping formats omit methods if they are overriding a parent's method
		// when baseData's obfuscation lookup fails, try to look up the top parent, since there is no
		// drawback but efficiency
		if(
			this.parent.elem != null
				&& (overriddenStub != null || !lookupOutcome[0])
				&& (this.elem == null ^ overriddenStub == null)
		) {
			// if the Overridden annotation specified a signature, use it, otherwise try to figure it out
			ExecutableElement top;
			if(overriddenStub != null) {
				Overridden o = overriddenStub.getAnnotation(Overridden.class);
				top = (ExecutableElement) findMember(
					ClassContainer.from(
						o,
						Overridden::parent,
						o.parentFqn(),
						o.parentInner(),
						options
					),
					overriddenStub.getSimpleName().toString(),
					o.strict() ? descriptorFromExecutableElement(overriddenStub, options.env) : null,
					o.strict(),
					false,
					options.env
				);
			} else top = findOverloadedMethod(this.parent.elem, this.elem, options.env);
			ClassData topParentData = getClassData(
				internalNameFromType(top.getEnclosingElement().asType(), options.env),
				options.mapper
			);

			MethodData topData = getMethodData(
				topParentData.name,
				top.getSimpleName().toString(),
				descriptorFromExecutableElement(top, options.env),
				options.mapper,
				null
			);

			this.data = new MethodData(
				parent.data,
				baseData.signature.name,
				topData.nameMapped,
				baseData.signature.descriptor
			);
		} else this.data = baseData;

		if(strict) {
			descriptor = this.data.signature.descriptor;
		}

		this.descriptorObf = options.mapper == null
			? descriptor
			: MappingUtils.mapMethodDescriptor(descriptor, options.mapper, false);
	}

	/**
	 * Builds the {@link MethodContainer} corresponding to a stub annotated with {@link Target}.
	 * @param stub the {@link ExecutableElement} for the stub
	 * @param t the {@link Target} annotation relevant to this case
	 * @param f the {@link Find} annotation containing fallback data, may be null
	 * @param opts the {@link ProcessorOptions} to be used
	 * @return the {@link MethodContainer} corresponding to the method
	 * @throws AmbiguousDefinitionException if it finds more than one candidate
	 * @throws TargetNotFoundException if it finds no valid candidate
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
			findOverriddenStub(stub),
			opts
		);
	}

	/**
	 * Find the associated {@link Overridden} stub, if present.
	 * @param stub the stub to look for info
	 * @return the {@link Overridden} stub, or null if not found
	 */
	public static ExecutableElement findOverriddenStub(ExecutableElement stub) {
		List<ExecutableElement> elements = stub.getEnclosingElement().getEnclosedElements().stream()
			.filter(e -> e instanceof ExecutableElement)
			.map(e -> (ExecutableElement) e)
			.filter(e -> {
				if(e.getParameters().size() != stub.getParameters().size()) return false;
				Overridden ann = e.getAnnotation(Overridden.class);
				if(ann == null) return false;

				CharSequence nameLookingFor = ann.by().isEmpty()
					? stub.getSimpleName()
					: ann.by();
				return stub.getSimpleName().equals(nameLookingFor);
			}).collect(Collectors.toList());

		switch(elements.size()) {
			case 0:
				return null;
			case 1:
				return elements.get(0);
			default:
				throw new AmbiguousDefinitionException(String.format(
					"Found multiple @Overridden methods for stub %s.%s!",
					((QualifiedNameable) stub.getEnclosingElement()).getQualifiedName().toString(),
					stub.getSimpleName().toString()
				));
		}
	}
}
