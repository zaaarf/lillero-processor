package ftbsc.lll.processor.utils;

import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTreeView;

/**
 * Wrapper around a mappings-io {@link MappingTree} that provides an API
 * that is friendlier to our use-case.
 */
public class Mapper {
	/**
	 * The {@link MappingTreeView} representing the mappings.
	 */
	private final MappingTreeView tree;

	/**
	 * The index within {@link #tree} of the namespace to map from.
	 */
	private final int from;

	/**
	 * The index within {@link #tree} of the namespace to map to.
	 */
	private final int to;

	/**
	 * Creates a new mapper from the given parsed tree.
	 * The given namespaces must either be null or be guaranteed to be valid.
	 * @param tree the tree that contains the info
	 * @param from the namespace to map from
	 * @param to the namespace to map to
	 */
	public Mapper(MappingTree tree, String from, String to) {
		this.tree = tree;
		this.from = from != null
			? this.tree.getNamespaceId(from)
			: MappingTree.SRC_NAMESPACE_ID;
		this.to = to != null
			? this.tree.getNamespaceId(to)
			: this.tree.getMaxNamespaceId() - 1;
	}

	/**
	 * Maps a class name according to this mapper.
	 * @param name the name of the class
	 * @return the mapped name
	 */
	public String mapClassName(String name) {
		MappingTree.ClassMappingView cls = this.tree.getClass(name, this.from);
		if(cls == null) {
			return name;
		}

		String mapped = cls.getName(this.to);
		return mapped != null ? mapped : name;
	}

	/**
	 * Maps a method name according to this mapper.
	 * @param parent the parent class
	 * @param name the name of the method
	 * @param descriptor the descriptor of the method
	 * @return the mapped name
	 */
	public String mapMethodName(String parent, String name, String descriptor) {
		MappingTree.ClassMappingView cls = tree.getClass(parent, this.from);
		if(cls == null) {
			return name;
		}

		MappingTree.MethodMappingView m = cls.getMethod(name, descriptor, this.from);
		if(m == null) {
			return name;
		}

		String mapped = m.getName(this.to);
		return mapped != null ? mapped : name;
	}

	/**
	 * Maps a field name according to this mapper.
	 * @param parent the parent class
	 * @param name the name of the field
	 * @param descriptor the descriptor of the field
	 * @return the mapped name
	 */
	public String mapFieldName(String parent, String name, String descriptor) {
		MappingTree.ClassMappingView cls = this.tree.getClass(parent, this.from);
		if(cls == null) {
			return name;
		}

		MappingTree.FieldMappingView field = cls.getField(name, descriptor, this.from);
		if(field == null) {
			return name;
		}

		String mapped = field.getName(this.to);
		return mapped != null ? mapped : name;
	}

	/**
	 * Maps a descriptor according to this mapper.
	 * @param descriptor the descriptor to map
	 * @return the mapped descriptor
	 */
	public String mapDescriptor(String descriptor) {
		StringBuilder result = new StringBuilder();
		int i = 0;
		while(i < descriptor.length()) {
			if(descriptor.charAt(i) == 'L') {
				int closing = descriptor.indexOf(';', i + 1);
				if(closing == -1) {
					result.append(descriptor.substring(i));
					break;
				}

				String className = descriptor.substring(i + 1, closing);
				MappingTree.ClassMappingView mapping = this.tree.getClass(className, this.from);
				if(mapping != null) {
					className = mapping.getName(this.to);
				}

				result.append('L').append(className).append(';');
				i = closing + 1;
			} else {
				result.append(descriptor.charAt(i));
				i++;
			}
		}

		return result.toString();
	}

	/**
	 * Checks if there is a mapping for the given method.
	 * @param parent the parent
	 * @param name the method name
	 * @param descriptor the method descriptor
	 * @return true if a mapping existed
	 */
	public boolean hasMethod(String parent, String name, String descriptor) {
		MappingTree.ClassMappingView cls = this.tree.getClass(parent, this.from);
		if(cls == null) {
			return false;
		}

		return cls.getMethod(name, descriptor, this.from) != null;
	}
}
