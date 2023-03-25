package ftbsc.lll.exceptions;

import ftbsc.lll.processor.annotations.Find;

/**
 * Thrown when a method is annotated with {@link Find} but does not
 * return a known instance of {@link ftbsc.lll.proxies.AbstractProxy}.
 * @since 0.5.0
 */
public class NotAProxyException extends RuntimeException {

	/**
	 * Constructs an exception for the specified method.
	 * @param parent the FQN of the class containing the method
	 * @param method the name of the method wrongly annotated
	 */
	public NotAProxyException(String parent, String method) {
		super(String.format("Annotated field %s::%s does not return a proxy!", parent, method));
	}
}
