package ftbsc.lll.processor.reporting;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/**
 * A special type of {@link RuntimeException} that can be reported on the {@link Messager}.
 * @since 0.9.5
 */
public class Reportable extends RuntimeException {
	/**
	 * Constructs a new reportable exception, formatting the given arguments into the message.
	 * You probably wanted to call one of the static factory methods of {@link ErrorReporter}.
	 *
	 * @param format the format (for {@link String#format(String, Object...)})
	 * @param params the params (for {@link String#format(String, Object...)})
	 */
	public Reportable(String format, Object... params) {
		super(String.format("Lillero: " + format, params));
	}

	/**
	 * Report this exception on the given messager.
	 *
	 * @param messager the messager to report on
	 * @param element  the element to report on
	 */
	public void report(Messager messager, Element element) {
		messager.printMessage(Diagnostic.Kind.ERROR, this.getMessage(), element);
	}
}
