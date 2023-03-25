package ftbsc.lll.exceptions;

/**
 * Thrown upon failure to find an existing method from a stub.
 */
public class TargetNotFoundException extends RuntimeException {

	/**
	 * Constructs a new target not found exception for the specified method stub.
	 * @param type the type of element being sought (class, method, etc.)
	 * @param member the stub's name (and descriptor possibly)
	 */
	public TargetNotFoundException(String type, String member) {
		super(String.format("Could not find target %s %s.", type, member));
	}

	/**
	 * Constructs a new target not found exception for the specified method stub.
	 * @param type the type of element being sought (class, method, etc.)
	 * @param member the stub's name (and descriptor possibly)
	 * @param parent the parent of the member
	 */
	public TargetNotFoundException(String type, String member, String parent) {
		super(String.format("Could not find target %s %s in class %s.", type, member, parent));
	}
}
