package ftbsc.lll.exceptions;

import ftbsc.lll.processor.tools.SrgMapper;

/**
 * Thrown upon failure to find the requested mapping within a loaded {@link SrgMapper}.
 */
public class MappingNotFoundException extends RuntimeException {

	/**
	 * Constructs a new mapping not found exception for the specified mapping.
	 * @param mapping the detail message
	 */
	public MappingNotFoundException(String mapping) {
		super("Could not find mapping for " + mapping + "!");
	}
}
