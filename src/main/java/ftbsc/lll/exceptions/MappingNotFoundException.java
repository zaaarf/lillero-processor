package ftbsc.lll.exceptions;

import ftbsc.lll.processor.tools.obfuscation.IMapper;

/**
 * Thrown upon failure to find the requested mapping within a loaded {@link IMapper}.
 */
public class MappingNotFoundException extends RuntimeException {

	/**
	 * Constructs a new mapping not found exception for the specified mapping.
	 * @param mapping the relevant mapping
	 */
	public MappingNotFoundException(String mapping) {
		super("Could not find mapping for " + mapping + "!");
	}

	/**
	 * Constructs a new mapping not found exception for the specified mapping
	 * with the specified reason.
	 * @param mapping the relevant mapping
	 * @param reason the reason message
	 */
	public MappingNotFoundException(String mapping, String reason) {
		this(mapping + ": " + reason);
	}
}
