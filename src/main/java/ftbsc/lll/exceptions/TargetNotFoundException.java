package ftbsc.lll.exceptions;

/**
 * Thrown upon failure to find an existing method from a stub.
 */
public class TargetNotFoundException extends RuntimeException {

	/**
	 * Constructs a new target not found exception for the specified method stub.
	 * @param type the type of element being sought (class, method, etc.)
	 * @param stub the stub's name (and descriptor possibly)
	 */
	public TargetNotFoundException(String type, String stub) {
		super(String.format("Could not find target %s %s.", type, stub));
	}
}
