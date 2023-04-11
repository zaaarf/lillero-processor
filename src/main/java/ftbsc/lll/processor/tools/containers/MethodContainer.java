package ftbsc.lll.processor.tools.containers;

import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.exceptions.TargetNotFoundException;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import static ftbsc.lll.processor.tools.ASTUtils.*;

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
	 * The obfuscated name of the method.
	 * If the mapper passed is null, then this will be identical to {@link #name}.
	 */
	public final String nameObf;

	/**
	 * The obfuscated descriptor of the field.
	 * If the mapper passed is null, then this will be identical to {@link #descriptor}.
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
	 * {@link #from(ExecutableElement, Target, Find, ProcessingEnvironment, ObfuscationMapper)}.
	 * @param parent the {@link ClassContainer} representing the parent
	 * @param name the fully-qualified name of the target method
	 * @param descriptor the descriptor of the target method
	 * @param strict whether the matching should be strict (see {@link Target#strict()} for more info).
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @param mapper the {@link ObfuscationMapper} to be used, may be null
	 */
	private MethodContainer(
		ClassContainer parent, String name, String descriptor,
		boolean strict, ProcessingEnvironment env, ObfuscationMapper mapper) {
		this.parent = parent;
		if(parent.elem == null) { //unverified
			if(descriptor == null)
				throw new AmbiguousDefinitionException("Cannot use name-based lookups for methods of unverifiable classes!");
			this.elem = null;
			this.name = name;
			this.descriptor = descriptor;
		} else {
			this.elem = findOverloadedMethod( //to prevent type erasure from messing it all up
				(TypeElement) this.parent.elem,
				(ExecutableElement) findMember(
					parent, name, descriptor, descriptor != null && strict,false, env
				), env
			);

			this.name = this.elem.getSimpleName().toString();
			this.descriptor = descriptorFromExecutableElement(this.elem, env);
		}
		this.descriptorObf = mapper == null ? this.descriptor : mapper.obfuscateMethodDescriptor(this.descriptor);
		this.nameObf = findMemberName(parent.fqn, this.name, this.descriptor, mapper);
	}

	/**
	 * Builds the {@link MethodContainer} corresponding to a stub annotated with {@link Target}.
	 * @param stub the {@link ExecutableElement} for the stub
	 * @param t the {@link Target} annotation relevant to this case
	 * @param f the {@link Find} annotation containing fallback data, may be null
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @param mapper the {@link ObfuscationMapper} to be used, may be null
	 * @return the {@link MethodContainer} corresponding to the method
	 * @throws AmbiguousDefinitionException if it finds more than one candidate
	 * @throws TargetNotFoundException if it finds no valid candidate
	 * @since 0.3.0
	 */
	public static MethodContainer from(ExecutableElement stub, Target t, Find f, ProcessingEnvironment env, ObfuscationMapper mapper) {
		//the parent always has a @Patch annotation
		Patch patchAnn = stub.getEnclosingElement().getAnnotation(Patch.class);
		ClassContainer parent = ClassContainer.findOrFallback(
			ClassContainer.from(patchAnn, Patch::value, patchAnn.className(), env, mapper),
			f, env, mapper
		);

		String name = t != null && !t.methodName().equals("")
			?	t.methodName() //name was specified in target
			: stub.getSimpleName().toString();
		String descriptor = t != null && t.strict()
			? descriptorFromExecutableElement(stub, env)
			: null;

		return new MethodContainer(parent, name, descriptor, t != null && t.strict(), env, mapper);
	}
}