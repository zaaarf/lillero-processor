package ftbsc.lll.processor.tools.containers;

import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.exceptions.TargetNotFoundException;
import ftbsc.lll.processor.LilleroProcessor;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;

import static ftbsc.lll.processor.tools.ASTUtils.findMember;
import static ftbsc.lll.processor.tools.ASTUtils.findMemberName;
import static ftbsc.lll.processor.tools.JavaPoetUtils.descriptorFromExecutableElement;

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
	 * The {@link ClassContainer} representing the parent of this method.
	 * May be null if the parent is a class type that can not be checked
	 * at processing time (such as an anonymous class)
	 */
	public final ClassContainer parent;

	/**
	 * The {@link ExecutableElement} corresponding to the method.
	 * May only be null intentionally i.e. when the method is
	 * a child of an anonymous class.
	 */
	public final ExecutableElement elem;

	/**
	 * Public constructor.
	 * @param parent the {@link ClassContainer} representing the parent
	 * @param name the fully-qualified name of the target method
	 * @param descriptor the descriptor of the target method
	 * @param strict whether the matching should be strict (see {@link Target#strict()} for more info).
	 * @param mapper the {@link ObfuscationMapper} to be used, may be null
	 */
	public MethodContainer(ClassContainer parent, String name, String descriptor, boolean strict, ObfuscationMapper mapper) {
		this.parent = parent;
		if(parent.elem == null) { //unverified
			if(descriptor == null)
				throw new AmbiguousDefinitionException("Cannot use name-based lookups for methods of unverifiable classes!");
			this.elem = null;
			this.name = name;
			this.descriptor = mapper == null ? descriptor : mapper.obfuscateMethodDescriptor(descriptor);
		} else {
			this.elem = (ExecutableElement) findMember(parent, name, descriptor, descriptor != null && strict, false);
			this.name = this.elem.getSimpleName().toString();
			String validatedDescriptor = descriptorFromExecutableElement(this.elem);
			this.descriptor = mapper == null ? descriptor : mapper.obfuscateMethodDescriptor(validatedDescriptor);
		}
		this.nameObf = findMemberName(parent.fqnObf, name, descriptor, mapper);
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

		String name, descriptor;
		if(f != null && !f.name().equals("")) { //match by name alone
			if(LilleroProcessor.badPracticeWarnings) //warn user that he is doing bad stuff
				env.getMessager().printMessage(Diagnostic.Kind.WARNING,
					String.format("Matching method %s by name, this is bad practice and may lead to unexpected behaviour. Use @Target stubs instead!", f.name()));
			name = f.name();
			descriptor = null;
		} else {
			if(t != null && !t.methodName().equals(""))
				name = t.methodName(); //name was specified in target
			else name = stub.getSimpleName().toString();
			descriptor = t != null && t.strict() ? descriptorFromExecutableElement(stub) : null;
		}

		return new MethodContainer(parent, name, descriptor, t != null && t.strict(), mapper);
	}
}