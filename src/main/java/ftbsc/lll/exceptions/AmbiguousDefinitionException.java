package ftbsc.lll.exceptions;

/**
 * Thrown when the processor finds multiple methods matching the given criteria.
 */
public class AmbiguousDefinitionException extends RuntimeException {

	/**
	 * Constructs a new ambiguous definition exception with the specified detail message.
	 * @param message the detail message
	 */
	public AmbiguousDefinitionException(String message) {
		super(message);
	}
}
