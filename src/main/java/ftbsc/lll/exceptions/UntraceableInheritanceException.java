package ftbsc.lll.exceptions;

import ftbsc.lll.processor.containers.ClassContainer;

import javax.lang.model.element.Element;

/**
 * Thrown before the processor tries fails to trace inheritance for a class.
 */
public class UntraceableInheritanceException extends RuntimeException {

	/**
	 * Constructs a new untraceable inheritance exception with the specified detail message.
	 * @param parent the parent in question
	 */
	public UntraceableInheritanceException(ClassContainer parent) {
		super(String.format("Can't check inherited fields on manually typed class %s!", parent.data.name));
	}

	/**
	 * Constructs a new untraceable inheritance exception with the specified detail message.
	 * @param parent where it was traced to
	 * @param member the found member
	 * @param from where it was traced from
	 */
	public UntraceableInheritanceException(ClassContainer parent, Element member, ClassContainer from) {
		super(String.format(
			"Found inherited member %s::%s, but it's unreachable from context %s!",
			parent.data.name,
			member.getEnclosingElement().getSimpleName().toString(),
			from.data.name
		));
	}
}
