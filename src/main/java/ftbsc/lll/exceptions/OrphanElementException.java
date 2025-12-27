package ftbsc.lll.exceptions;

import javax.lang.model.element.Element;
import javax.lang.model.element.QualifiedNameable;
import java.util.ArrayDeque;
import java.util.Queue;

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
		super(String.format("Could not find a valid target for element %s!", buildPath(element)));
	}

	private static String buildPath(Element element) {
		ArrayDeque<String> name = new ArrayDeque<>();
		Element cur = element;
		while(!(cur instanceof QualifiedNameable)) {
			name.push(cur.getSimpleName().toString());
			cur = element.getEnclosingElement();
		}

		return ((QualifiedNameable) cur).getQualifiedName().toString()
			+ "::"
			+ String.join(".", name);
	}
}
