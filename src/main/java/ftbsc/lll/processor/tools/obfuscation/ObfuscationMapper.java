package ftbsc.lll.processor.tools.obfuscation;

import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.exceptions.MappingNotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses a .tsrg file into a mapper capable of converting from
 * deobfuscated names to obfuscated ones.
 * Obviously, it may only be used at runtime if the .tsrg file is
 * included in the resources. However, in that case, I'd recommend
 * using the built-in Forge one and refrain from including an extra
 * resource for no good reason.
 * TODO: CSV format
 * @since 0.2.0
 */
public class ObfuscationMapper {

	/**
	 * A Map using the deobfuscated names as keys,
	 * holding information for that class as value.
	 */
	private final Map<String, ObfuscationData> mapper = new HashMap<>();

	/**
	 * The public constructor.
	 * Should be passed a {@link Stream} of Strings, one representing each line.
	 * Whether they contain line endings or not is irrelevant.
	 * @param str a {@link Stream} of strings
	 */
	public ObfuscationMapper(Stream<String> str) {
		AtomicReference<String> currentClass = new AtomicReference<>("");
		str.forEach(l -> {
			if(l.startsWith("\t"))
				mapper.get(currentClass.get()).addMember(l);
			else {
				String[] sp = l.split(" ");
				ObfuscationData s = new ObfuscationData(sp[0], sp[1]);
				currentClass.set(s.unobf);
				mapper.put(s.unobf, s);
			}
		});
	}

	/**
	 * Gets the obfuscated name of the class.
	 * @param name the unobfuscated internal name of the desired class
	 * @return the obfuscated name of the class
	 * @throws MappingNotFoundException if no mapping is found
	 */
	public String obfuscateClass(String name)  {
		ObfuscationData data = mapper.get(name.replace('.', '/'));
		if(data == null)
			throw new MappingNotFoundException(name);
		else return data.obf;
	}

	/**
	 * Gets the unobfuscated name of the class.
	 * Due to how it's implemented, it's considerably less efficient than its
	 * opposite operation.
	 * @param obfName the obfuscated internal name of the desired class
	 * @return the deobfuscated name of the class
	 */
	public String deobfuscateClass(String obfName) {
		ObfuscationData data = getObfuscationData(obfName);
		return data.unobf;
	}

	/**
	 * Gets the obfuscated name of a class member (field or method).
	 * The method signature must be in this format: "methodName methodDescriptor",
	 * with a space, because that's how it is in .tsrg files.
	 * @param parentName the unobfuscated internal name of the parent class
	 * @param memberName the field name or method signature
	 * @param methodDescriptor the optional descriptor of the member, may be null or partial
	 * @return the obfuscated name of the given member
	 * @throws MappingNotFoundException if no mapping is found
	 */
	public String obfuscateMember(String parentName, String memberName, String methodDescriptor) {
		ObfuscationData data = mapper.get(parentName.replace('.', '/'));
		if(data == null)
			throw new MappingNotFoundException(parentName + "::" + memberName);
		String member = data.get(memberName, methodDescriptor);
		if(member == null)
			throw new MappingNotFoundException(parentName + "::" + memberName);
		return member;
	}

	/**
	 * Gets the unobfuscated name of the given member.
	 * Due to how it's implemented, it's considerably less efficient than its
	 * opposite operation.
	 * @param parentObf the obfuscated internal name of the container class
	 * @param memberObf the field name or method signature
	 * @return the deobfuscated name of the given member
	 */
	public String deobfuscateMember(String parentObf, String memberObf) {
		ObfuscationData data = getObfuscationData(parentObf);
		for(String unobf : data.members.keySet())
			if(data.members.get(unobf).equals(memberObf))
				return unobf;
		return null;
	}

	/**
	 * Used internally. Gets the obfuscation data corresponding to the given obfuscated class name.
	 * @param classObfuscatedName the internal name of the obfuscated class
	 * @return the desired {@link ObfuscationData} object
	 * @throws MappingNotFoundException if no {@link ObfuscationData} object is found
	 */
	private ObfuscationData getObfuscationData(String classObfuscatedName) {
		for(ObfuscationData s : mapper.values())
			if(s.obf.equals(classObfuscatedName))
				return s;
		throw new MappingNotFoundException(classObfuscatedName);
	}

	/**
	 * Private class used internally for storing information about each
	 * class. It's private because there is no good reason anyone would
	 * want to access this outside of this class.
	 */
	private static class ObfuscationData {
		/**
		 * The unobfuscated name (FQN with '/' instad of '.') of the class.
		 */
		private final String unobf;

		/**
		 * The obfuscated internal name (FQN with '/' instad of '.') of the class.
		 */
		private final String obf;

		/**
		 * A {@link Map} tying each member's name or signature to its
		 * obfuscated counterpart.
		 */
		private final Map<String, String> members;

		/**
		 * The constructor. It takes in the names (obfuscated and non-obfuscated)
		 * of a class.
		 * @param unobf the unobfuscated name
		 * @param obf the obfuscated name
		 */
		private ObfuscationData(String unobf, String obf) {
			this.unobf = unobf;
			this.obf = obf;
			this.members = new HashMap<>();
		}

		/**
		 * Adds a member to the target class.
		 * For fields only the names are required; for methods,
		 * this takes in the full signature ({@code name + " " + space}).
		 * @param s the String representing the declaration line
		 */
		public void addMember(String s) {
			String[] split = s.trim().split(" ");
			if(split.length == 2) //field
				members.put(split[0], split[1]);
			else if (split.length == 3) //method
				members.put(split[0] + " " + split[1], split[2]);
		}

		/**
		 * Gets an obfuscated member given the method name and a method descriptor,
		 * which may be partial (i.e. not include return type) or null if the member
		 * is not a method.
		 * @param memberName member name
		 * @param methodDescriptor the method descriptor, or null if it's not a method
		 * @return the requested obfuscated name, or null if nothing was found
		 * @throws AmbiguousDefinitionException if not enough data was given to uniquely identify a mapping
		 */
		public String get(String memberName, String methodDescriptor) {
			if(methodDescriptor == null)
				return members.get(memberName);
			List<String> candidates = members.keySet().stream().filter(m -> m.startsWith(memberName)).collect(Collectors.toList());
			if(candidates.size() == 1)
				return members.get(candidates.get(0));
			String signature = memberName + " " + methodDescriptor;
			candidates = candidates.stream().filter(m -> m.startsWith(signature)).collect(Collectors.toList());
			switch(candidates.size()) {
				case 0:
					return null;
				case 1:
					return members.get(candidates.get(0));
				default:
					throw new AmbiguousDefinitionException("Mapper could not uniquely identify method " + unobf + "::" + memberName);
			}
		}
	}
}
