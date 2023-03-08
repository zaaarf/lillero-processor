package ftbsc.lll.exceptions;

/**
 * Thrown upon failure to find an existing method from a stub.
 */
public class TargetNotFoundException extends RuntimeException {

	/**
	 * Constructs a new target not found exception for the specified method stub.
	 * @param stub the stub's name (and descriptor possibly)
	 */
	public TargetNotFoundException(String stub) {
		super("Could not find member corresponding to stub: " + stub);
	}
}
