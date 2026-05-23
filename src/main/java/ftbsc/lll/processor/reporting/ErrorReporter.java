package ftbsc.lll.processor.reporting;

import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Overridden;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.containers.ClassContainer;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;

/**
 * Reports errors to the messenger.
 * @since 0.9.5
 */
public class ErrorReporter {

	/**
	 * Reports an error due a non-proxy element annotated with {@link Find}.
	 * @param element the element to report on
	 * @return the exception to throw
	 */
	public static Reportable notAProxy(VariableElement element) {
		return new Reportable("Annotated element %s does is not a proxy!", buildPath(element));
	}

	/**
	 * Reports an error due to inheritance (see {@link Find#inherited()}) being untraceable.
	 * @param parent the parent in question
	 * @return the exception to throw
	 */
	public static Reportable untraceableInheritance(ClassContainer parent) {
		return new Reportable("Can't check inherited fields on manually typed class %s!", parent.name);
	}

	/**
	 * Reports an error due to inheritance (see {@link Find#inherited()}) being untraceable.
	 * @param parent where it was traced to
	 * @param member the found member
	 * @param from where it was traced from
	 * @return the exception to throw
	 */
	public static Reportable untraceableInheritance(ClassContainer parent, Element member, ClassContainer from) {
		return new Reportable(
			"Found inherited member %s::%s, but it's unreachable from context %s!",
			parent.name,
			member.getEnclosingElement().getSimpleName().toString(),
			from.name
		);
	}

	/**
	 * Reports an error due to asking the processor to look up names of members of unverifiable classes.
	 * @param type the {@link MemberType}
	 * @return the exception to throw
	 */
	public static Reportable badNameBasedLookup(MemberType type) {
		return new Reportable("Cannot use name-based lookups for %s of unverifiable classes!", type.plural);
	}

	/**
	 * Reports an error due to annotated element that needs to be paired with another not matching any.
	 * @param element the element in question
	 * @return the exception to throw
	 */
	public static Reportable orphan(Element element) {
		return new Reportable("Could not find a valid target for element %s!", buildPath(element));
	}

	/**
	 * Reports an error due to there being multiple {@link Overridden} classes for the same stub.
	 * @param stub the stub
	 * @return the exception to throw
	 */
	public static Reportable ambiguousOverridden(ExecutableElement stub) {
		return new Reportable(
			"Found multiple @Overridden methods for stub %s!",
			buildPath(stub)
		);
	}

	/**
	 * Reports an error due to a name-based lookup for {@link Find} resulting in ambiguity.
	 * @param memberType the type of member we are looking up
	 * @param candidates the number of found candidates
	 * @param lookupName the name being looked up
	 * @param className the class where it's being looked up
	 * @return the exception to throw
	 */
	public static Reportable ambiguousLookup(
		MemberType memberType,
		int candidates,
		String lookupName,
		String className
	) {
		return new Reportable(
			"Found %d %s named \"%s\" in class %s!",
			candidates,
			memberType.plural,
			lookupName,
			className
		);
	}

	/**
	 * Reports an error due to the user using {@link Target#of()} ambiguously.
	 * @param name the ambiguous name
	 * @param candidates the number of candidates
	 * @return the exception to throw
	 */
	public static Reportable ambiguousOf(String name, int candidates) {
		return new Reportable("Target specified name \"%s\", but %d candidates matched it.", name, candidates);
	}

	/**
	 * Reports an error due to the requested member not being found.
	 * @param adjective an adjective to add to the type (nullable)
	 * @param type the type of element being sought (class, method, etc.)
	 * @param name the stub's name
	 * @param descriptor the descriptor (nullable)
	 * @param parent the parent of the member (nullable)
	 * @return the exception to throw
	 */
	public static Reportable notFound(
		String adjective,
		MemberType type,
		String name,
		String descriptor,
		String parent
	) {
		StringBuilder bd = new StringBuilder("Could not find target ");

		if(adjective != null) {
			bd.append(adjective).append(" ");
		}

		bd.append(type.singular).append(" with name \"").append(name).append("\"");

		if(descriptor != null) {
			bd.append(" and descriptor ").append(descriptor);
		}

		if(parent != null) {
			bd.append(" in class ").append(parent);
		}

		bd.append(".");

		return new Reportable(bd.toString());
	}

	/**
	 * Reports an error due to the requested member not being found.
	 * @param adjective an adjective to add to the type (nullable)
	 * @param type the type of element being sought (class, method, etc.)
	 * @param name the stub's name
	 * @return the exception to throw
	 */
	public static Reportable notFound(String adjective, MemberType type, String name) {
		return notFound(adjective, type, name, null, null);
	}

	/**
	 * Reports an error due to the requested member not being found.
	 * @param type the type of element being sought (class, method, etc.)
	 * @param name the stub's name
	 * @param parent the parent of the member (nullable)
	 * @return the exception to throw
	 */
	public static Reportable notFound(MemberType type, String name, String parent) {
		return notFound(null, type, name, null, parent);
	}

	private static String buildPath(Element element) {
		ArrayDeque<String> name = new ArrayDeque<>();
		Element cur = element;
		while(!(cur instanceof QualifiedNameable)) {
			name.push(cur.getSimpleName().toString());
			cur = cur.getEnclosingElement();
		}

		return ((QualifiedNameable) cur).getQualifiedName().toString()
			+ "::"
			+ String.join("::", name);
	}

	/**
	 * Reports the given runtime exception if it is reportable, or throws it again.
	 * @param environment the environment to report this for
	 * @param exception the exception to handle
	 * @param element the element to report on
	 */
	public static void handleRuntimeException(
		ProcessingEnvironment environment,
		RuntimeException exception,
		Element element
	) {
		if(exception instanceof Reportable) {
			((Reportable) exception).report(environment.getMessager(), element);
		} else {
			throw exception;
		}
	}

	/**
	 * Puts a {@link Throwable}'s stacktrace into a string.
	 * @param t the throwable to get the stacktrace for
	 * @return the stacktrace as string
	 */
	public static String stacktraceToString(Throwable t) {
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}
}
