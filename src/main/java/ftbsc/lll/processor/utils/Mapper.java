package ftbsc.lll.processor.utils;

import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTreeView;

/**
 * Wrapper around a mappings-io {@link MappingTree} that provides an API
 * that is friendlier to our use-case.
 */
public class Mapper {
	private final MappingTreeView tree;
	private final int from, to;

	public Mapper(MappingTree tree, String from, String to) {
		this.tree = tree;
		this.from = from != null
			? this.tree.getNamespaceId(from)
			: MappingTree.SRC_NAMESPACE_ID;
		this.to = to != null
			? this.tree.getNamespaceId(to)
			: this.tree.getMaxNamespaceId();
	}

	public String mapClass(String name) {
		MappingTree.ClassMappingView cls = this.tree.getClass(name, this.from);
		if(cls == null) {
			return name;
		}

		String mapped = cls.getName(this.to);
		return mapped != null ? mapped : name;
	}

	public String mapMethod(String owner, String name, String desc) {
		MappingTree.ClassMappingView cls = tree.getClass(owner, this.from);
		if(cls == null) {
			return name;
		}

		MappingTree.MethodMappingView m = cls.getMethod(name, desc, this.from);
		if(m == null) {
			return name;
		}

		String mapped = m.getName(this.to);
		return mapped != null ? mapped : name;
	}

	public String mapFieldName(String owner, String name, String descriptor) {
		MappingTree.ClassMappingView cls = this.tree.getClass(owner, this.from);
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
	 * Maps a descriptor according to the given mapper.
	 * @param desc the descriptor to map
	 * @param reverse whether to map 'to' to 'from' instead
	 * @return the mapped descriptor
	 */
	public String mapDescriptor(String desc, boolean reverse) {
		StringBuilder result = new StringBuilder();
		int i = 0;
		while(i < desc.length()) {
			if(desc.charAt(i) == 'L') {
				int closing = desc.indexOf(';', i + 1);
				if(closing == -1) {
					result.append(desc.substring(i));
					break;
				}

				String className = desc.substring(i + 1, closing);
				MappingTree.ClassMappingView mapping = this.tree.getClass(className, reverse ? this.to : this.from);
				if(mapping != null) {
					className = mapping.getName(reverse ? this.from : this.to);
				}

				result.append(className);
				i = closing + 1;
			} else {
				result.append(desc.charAt(i));
				i++;
			}
		}

		return result.toString();
	}

	/**
	 * Checks if there is a mapping for the given method.
	 * @param owner the owner
	 * @param name the method name
	 * @param descriptor the method descriptor
	 * @return true if a mapping existed
	 */
	public boolean hasMethod(String owner, String name, String descriptor) {
		MappingTree.ClassMappingView cls = this.tree.getClass(owner, this.from);
		if(cls == null) {
			return false;
		}

		return cls.getMethod(name, descriptor, this.from) != null;
	}
}
