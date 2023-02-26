package ftbsc.lll.processor.exceptions;

/**
 * Thrown upon failure to locate the output.tsrg file at runtime.
 */
public class MappingsFileNotFoundException extends RuntimeException {
	public MappingsFileNotFoundException() {
		super("Could not find a mappings file in the specified location!");
	}
}
