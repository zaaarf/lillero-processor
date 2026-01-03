package ftbsc.lll.processor.reporting;

/**
 * The type of member, used by the {@link ErrorReporter} for message formatting.
 * @since 0.9.5
 */
public enum MemberType {
	/**
	 * Member is a type (class or interface).
	 */
	CLASS("classes"),

	/**
	 * Member is a method.
	 */
	METHOD("methods"),

	/**
	 * Member is a field.
	 */
	FIELD("fields");

	/**
	 * The singular form of the type.
	 */
	public final String singular;

	/**
	 * The plural form of the type.
	 */
	public final String plural;

	MemberType(String plural) {
		this.singular = this.name().toLowerCase();
		this.plural = plural;
	}
}
