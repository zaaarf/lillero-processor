package ftbsc.lll.exceptions;

/**
 * Thrown when a resource passed as an argument is not found.
 */
public class InvalidResourceException extends RuntimeException {

	/**
	 * Empty constructor, used when the provided resource exists but is empty.
	 */
	public InvalidResourceException() {
		super("The specified resource was empty!");
	}

	/**
	 * Named constructor, used when the specified resource doesn't exist.
	 * @param name the resource name
	 */
	public InvalidResourceException(String name) {
		super(String.format("Specified resource %s was not found!", name));
	}
}
