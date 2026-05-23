package ftbsc.lll.processor.containers;

import ftbsc.lll.processor.reporting.ErrorReporter;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.ProcessorOptions;
import ftbsc.lll.processor.reporting.MemberType;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import static ftbsc.lll.processor.utils.ASTUtils.*;

/**
 * Container for information about a field.
 * Used internally for efficiency reasons.
 * @since 0.5.0
 */
public class FieldContainer {
	/**
	 * The name of the field.
	 */
	public final String name;

	/**
	 * The descriptor of the field.
	 */
	public final String descriptor;

	/**
	 * The mapped name of the class.
	 * Will be identical to {@link #name} if no mappings were given.
	 */
	public final String nameMapped;

	/**
	 * The obfuscated descriptor of the field.
	 * Will be identical to {@link #descriptor} if no mappings were given.
	 */
	public final String descriptorMapped;

	/**
	 * The {@link ClassContainer} representing the parent of this field.
	 */
	public final ClassContainer parent;

	/**
	 * The {@link VariableElement} corresponding to the field.
	 * May only be null intentionally i.e. when the field is
	 * a child of an anonymous class.
	 */
	public final VariableElement elem;

	/**
	 * Private constructor, called from {@link #from(VariableElement, ProcessorOptions)}.
	 * @param parent the {@link ClassContainer} representing the parent
	 * @param name the fully-qualified name of the target field
	 * @param descriptor the descriptor of the target field, may be null for verifiable fields
	 * @param inherited whether to match implicitly inherited fields (see {@link Find#inherited()} for more info)
	 * @param options the {@link ProcessorOptions} to be used
	 */
	private FieldContainer(
		ClassContainer parent,
		String name,
		String descriptor,
		boolean inherited,
		ProcessorOptions options
	) {
		this.parent = parent;
		if(parent.elem == null) { // unverified
			if(descriptor == null) {
				throw ErrorReporter.badNameBasedLookup(MemberType.FIELD);
			}

			this.elem = null;
			this.descriptor = descriptor;
		} else {
			this.elem = (VariableElement) findMember(parent, name, descriptor, descriptor != null, inherited, true, options);
			this.descriptor = descriptorFromType(this.elem.asType(), options.env);
			name = this.elem.getSimpleName().toString();
		}

		this.name = name;
		this.nameMapped = options.mapper.mapFieldName(parent.name, name, this.descriptor);
		this.descriptorMapped = options.mapper.mapDescriptor(this.descriptor);
	}

	/**
	 * Finds a {@link FieldContainer} from a finder.
	 * @param finder the {@link VariableElement} annotated with {@link Find} for this field
	 * @param opts the {@link ProcessorOptions} to be used
	 * @return the built {@link FieldContainer}
	 * @since 0.5.0
	 */
	public static FieldContainer from(VariableElement finder, ProcessorOptions opts) {
		// the parent always has a @Patch annotation
		Patch p = finder.getEnclosingElement().getAnnotation(Patch.class);
		// the finder always has a @Find annotation
		Find f = finder.getAnnotation(Find.class);

		// fallback is either the parent or the parent's parent (in case @Find is on a method parameter)
		TypeElement fallbackClass;
		if(finder.getEnclosingElement() instanceof ExecutableElement) {
			fallbackClass = (TypeElement) finder.getEnclosingElement().getEnclosingElement();
		} else {
			fallbackClass = (TypeElement) finder.getEnclosingElement();
		}

		ClassContainer parent = ClassContainer.findOrFallback(fallbackClass, p, f, opts);

		String name = f.name().isEmpty() ? finder.getSimpleName().toString() : f.name();
		String descriptor;
		TypeMirror fieldType = getTypeFromAnnotation(f, Find::type, opts.env);
		if(fieldType.toString().equals("java.lang.Object")) {
			descriptor = null;
		} else {
			if(fieldType.getKind() != TypeKind.VOID && !fieldType.getKind().isPrimitive()) {
				descriptor = String.format("L%s;", ClassContainer.from(
					f,
					Find::type,
					f.typeFqn(),
					f.typeInner(),
					opts
				).nameMapped);
			} else descriptor = descriptorFromType(fieldType, opts.env);
		}

		return new FieldContainer(parent, name, descriptor, f.inherited(), opts);
	}
}
