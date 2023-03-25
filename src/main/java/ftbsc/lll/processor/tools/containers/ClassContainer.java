package ftbsc.lll.processor.tools.containers;

import ftbsc.lll.exceptions.TargetNotFoundException;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;

import static ftbsc.lll.processor.tools.ASTUtils.findClassName;

/**
 * Container for information about a class.
 * Used internally for efficiency reasons.
 * @since 0.5.0
 */
public class ClassContainer {
	/**
	 * The fully-qualified name of the class.
	 */
	public final String fqn;

	/**
	 * The obfuscated fully-qualified name of the class.
	 * If the mapper passed is null, then this will be identical to {@link #fqn}
	 */
	public final String fqnObf;

	/**
	 * The {@link Element} corresponding to the class.
	 */
	public final Element elem;

	/**
	 * Public constructor.
	 * @param fqn the fully-qualified name of the target class
	 * @param env the {@link ProcessingEnvironment} to be used to locate the class
	 * @param mapper the {@link ObfuscationMapper} to be used, may be null
	 */
	public ClassContainer(String fqn, ProcessingEnvironment env, ObfuscationMapper mapper) {
		this.fqn = fqn;
		this.fqnObf = findClassName(fqn, mapper);
		Element elem = env.getElementUtils().getTypeElement(fqn); //at compile time we have an unobfuscated environment
		if(elem == null)
			throw new TargetNotFoundException("class", fqn);
		else this.elem = elem;
	}
}