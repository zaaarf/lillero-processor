package ftbsc.lll.exceptions;

import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.proxies.impl.FieldProxy;
import ftbsc.lll.proxies.impl.MethodProxy;

/**
 * Thrown when a method is annotated with {@link Find} but does not
 * return a {@link MethodProxy} or a {@link FieldProxy}
 */
public class NotAProxyException extends RuntimeException {

	/**
	 * Constructs a exception for the specified method.
	 * @param parent the FQN of the class containing the method
	 * @param method the name of the method wrongly annotated
	 */
	public NotAProxyException(String parent, String method) {
		super(String.format("Annotated field %s::%s does not return a proxy!", parent, method));
	}
}
