package ftbsc.lll.exceptions;

/**
 * Thrown when the processor finds multiple methods matching the given criteria.
 */
public class UntraceableInheritanceException extends RuntimeException {

	/**
	 * Constructs a new ambiguous definition exception with the specified detail message.
	 * @param message the detail message
	 */
	public UntraceableInheritanceException(String message) {
		super(message);
	}
}
