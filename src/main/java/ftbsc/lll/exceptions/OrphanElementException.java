package ftbsc.lll.exceptions;

import javax.lang.model.element.Element;
import javax.lang.model.element.QualifiedNameable;

/**
 * Thrown when an annotated element that needs to be paired with
 * another does not match with any.
 * @since 0.5.0
 */
public class OrphanElementException extends RuntimeException {
	/**
	 * Constructs an exception for the specified method.
	 * @param element the orphan element
	 */
	public OrphanElementException(Element element) {
		super(
			String.format(
				"Could not find a valid target for element %s.%s!",
				((QualifiedNameable) element.getEnclosingElement()).getQualifiedName().toString(),
				element.getSimpleName().toString()
			)
		);
	}
}
