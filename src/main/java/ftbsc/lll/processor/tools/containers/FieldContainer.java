package ftbsc.lll.processor.tools.containers;

import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import static ftbsc.lll.processor.tools.ASTUtils.*;
import static ftbsc.lll.processor.tools.JavaPoetUtils.descriptorFromType;

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
	 * The {@link ClassContainer} representing the parent of this field.
	 * May be null if the parent is a class type that can not be checked
	 * at processing time (such as an anonymous class)
	 */
	public final ClassContainer parent;

	/**
	 * The {@link VariableElement} corresponding to the field.
	 * May only be null intentionally i.e. when the field is
	 * a child of an anonymous class.
	 */
	public final VariableElement elem;

	/**
	 * Public constructor.
	 * @param parent the {@link ClassContainer} representing the parent
	 * @param name the fully-qualified name of the target field
	 * @param descriptor the descriptor of the target field, may be null for verifiable fields
	 * @param mapper the {@link ObfuscationMapper} to be used, may be null
	 */
	public FieldContainer(ClassContainer parent, String name, String descriptor, ObfuscationMapper mapper) {
		this.parent = parent;
		if(parent.elem == null) { //unverified
			if(descriptor == null)
				throw new AmbiguousDefinitionException("Cannot use name-based lookups for fields of unverifiable classes!");
			this.elem = null;
			this.name = name;
			this.descriptor = mapper == null ? descriptor : mapper.obfuscateMethodDescriptor(descriptor);
		} else {
			this.elem = (VariableElement) findMember(parent, name, descriptor, descriptor != null, true);
			this.name = this.elem.getSimpleName().toString();
			String validatedDescriptor = descriptorFromType(this.elem.asType());
			this.descriptor = mapper == null ? descriptor : mapper.obfuscateMethodDescriptor(validatedDescriptor);
		}
		this.nameObf = findMemberName(parent.fqnObf, name, descriptor, mapper);
	}

	/**
	 * Finds a {@link FieldContainer} from a finder.
	 * @param finder the {@link VariableElement} annotated with {@link Find} for this field
	 * @param env the {@link ProcessingEnvironment} to perform the operation in
	 * @param mapper the {@link ObfuscationMapper} to be used, may be null
	 * @return the built {@link FieldContainer}
	 * @since 0.5.0
	 */
	public static FieldContainer from(VariableElement finder, ProcessingEnvironment env, ObfuscationMapper mapper) {
		//the parent always has a @Patch annotation
		Patch patchAnn = finder.getEnclosingElement().getAnnotation(Patch.class);
		//the finder always has a @Find annotation
		Find f = finder.getAnnotation(Find.class);

		ClassContainer parent = ClassContainer.findOrFallback(
			ClassContainer.from(patchAnn, Patch::value, patchAnn.className(), env, mapper),
			f, env, mapper
		);

		String name = f.name().equals("") ? finder.getSimpleName().toString() : f.name();
		String descriptor;
		TypeMirror fieldType = getTypeFromAnnotation(f, Find::type, env);
		if(fieldType.toString().equals("java.lang.Object"))
			descriptor = null;
		else {
			if(fieldType.getKind() == TypeKind.DECLARED)
				descriptor = //jank af but this is temporary anyway
					"L" + new ClassContainer(
						fieldType.toString(),
						f.typeInner().equals("") ? null : f.typeInner().split("//$"),
						env,
						mapper
					).fqn.replace('.', '/') + ";";
			else descriptor = descriptorFromType(fieldType);
		}

		return new FieldContainer(parent, name, descriptor, mapper);
	}
}