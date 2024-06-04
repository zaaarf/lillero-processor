package ftbsc.lll.exceptions;

/**
 * Thrown when the user provides manually an invalid class name.
 */
public class InvalidClassNameException extends RuntimeException {

	/**
	 * Constructs a new exception for the specified name.
	 * @param name the name in question
	 */
	public InvalidClassNameException(String name) {
		super(String.format("Provided class name %s is not valid!", name));
	}
}
