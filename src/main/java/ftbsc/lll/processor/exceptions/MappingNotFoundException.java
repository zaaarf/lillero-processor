package ftbsc.lll.processor.exceptions;

public class MappingNotFoundException extends RuntimeException {
	public MappingNotFoundException(String mapping) {
		super("Could not find mapping for " + mapping + "!");
	}
}
