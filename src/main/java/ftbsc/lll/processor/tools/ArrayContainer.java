package ftbsc.lll.processor.tools;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Utility class that extrapolates information from a {@link TypeMirror},
 * making it considerably easier to get informations about an
 * array.
 * @since 0.4.0
 */
public class ArrayContainer {
	/**
	 * The nesting level of the array - a type who is not an array will have 0.
	 */
	public final int arrayLevel;

	/**
	 * The innermost component of the array, corresponding to the type of the base
	 * component.
	 */
	public final TypeMirror innermostComponent;

	/**
	 * Creates a new {@link ArrayContainer} from a {@link TypeMirror}.
	 * @param t the {@link TypeMirror} representing the type.
	 */
	public ArrayContainer(TypeMirror t) {
		int arrayLevel = 0;
		while(t.getKind() == TypeKind.ARRAY) {
			t = ((ArrayType) t).getComponentType();
			arrayLevel++;
		}
		this.arrayLevel = arrayLevel;
		this.innermostComponent = t;
	}
}
