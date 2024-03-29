package ftbsc.lll.processor.tools.containers;

import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.tools.ProcessorOptions;
import org.objectweb.asm.Type;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import static ftbsc.lll.processor.tools.ASTUtils.*;

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
	 * The obfuscated name of the field.
	 * If the mapper passed is null, then this will be identical to {@link #name}.
	 */
	public final String nameObf;

	/**
	 * The obfuscated descriptor of the field.
	 * If the mapper passed is null, then this will be identical to {@link #descriptor}.
	 */
	public final String descriptorObf;

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
	 * @param options the {@link ProcessorOptions} to be used
	 */
	private FieldContainer(ClassContainer parent, String name, String descriptor, ProcessorOptions options) {
		this.parent = parent;
		if(parent.elem == null) { //unverified
			if(descriptor == null)
				throw new AmbiguousDefinitionException("Cannot use name-based lookups for fields of unverifiable classes!");
			this.elem = null;
			this.name = name;
			this.descriptor = descriptor;
		} else {
			this.elem = (VariableElement) findMember(parent, name, descriptor, descriptor != null, true, options.env);
			this.name = this.elem.getSimpleName().toString();
			this.descriptor = descriptorFromType(this.elem.asType(), options.env);
		}
		this.descriptorObf = options.mapper == null ? this.descriptor : options.mapper.obfuscateType(Type.getType(this.descriptor)).getDescriptor();
		this.nameObf = findMemberName(parent.fqn, this.name, null, options.mapper);
	}

	/**
	 * Finds a {@link FieldContainer} from a finder.
	 * @param finder the {@link VariableElement} annotated with {@link Find} for this field
	 * @param options the {@link ProcessorOptions} to be used
	 * @return the built {@link FieldContainer}
	 * @since 0.5.0
	 */
	public static FieldContainer from(VariableElement finder, ProcessorOptions options) {
		//the parent always has a @Patch annotation
		Patch patchAnn = finder.getEnclosingElement().getAnnotation(Patch.class);
		//the finder always has a @Find annotation
		Find f = finder.getAnnotation(Find.class);

		ClassContainer parent = ClassContainer.findOrFallback(
			ClassContainer.from((TypeElement) finder.getEnclosingElement(), options), patchAnn, f, options
		);

		String name = f.name().equals("") ? finder.getSimpleName().toString() : f.name();
		String descriptor;
		TypeMirror fieldType = getTypeFromAnnotation(f, Find::type, options.env);
		if(fieldType.toString().equals("java.lang.Object")) {
			descriptor = null;
		} else {
			if(fieldType.getKind() != TypeKind.VOID && !fieldType.getKind().isPrimitive())
				descriptor = //jank af but this is temporary anyway
					"L" + ClassContainer.from(
						f, Find::type, f.typeInner(), options
					).fqnObf.replace('.', '/') + ";";
			else descriptor = descriptorFromType(fieldType, options.env);
		}

		return new FieldContainer(parent, name, descriptor, options);
	}
}
