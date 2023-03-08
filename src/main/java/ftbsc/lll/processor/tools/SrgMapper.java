package ftbsc.lll.processor.tools;

import ftbsc.lll.exceptions.MappingNotFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Parses a .tsrg file into a mapper capable of converting from
 * deobfuscated names to SRG names.
 * Obviously, it may only be used at runtime if the .tsrg file is
 * included in the resources. However, in that case, I'd recommend
 * using the built-in Forge one and refrain from including an extra
 * resource for no good reason.
 * @since 0.2.0
 */
public class SrgMapper {

	/**
	 * A Map using the deobfuscated names as keys,
	 * holding information for that Srg class as value.
	 */
	private final Map<String, ObfuscationData> mapper = new HashMap<>();

	/**
	 * The public constructor.
	 * Should be passed a {@link Stream} of Strings, one representing each line.
	 * Whether they contain line endings or not is irrelevant.
	 * @param str a {@link Stream} of strings
	 */
	public SrgMapper(Stream<String> str) {
		AtomicReference<String> currentClass = new AtomicReference<>("");
		str.forEach(l -> {
			if(l.startsWith("\t"))
				mapper.get(currentClass.get()).addMember(l);
			else {
				ObfuscationData s = new ObfuscationData(l);
				currentClass.set(s.mcpName);
				mapper.put(s.mcpName, s);
			}
		});
	}

	/**
	 * Gets the SRG-obfuscated name of the class.
	 * @param mcp the MCP (deobfuscated) internal name of the desired class
	 * @return the SRG name of the class
	 * @throws MappingNotFoundException if no mapping is found
	 */
	public String getSrgClass(String mcp)  {
		ObfuscationData data = mapper.get(mcp);
		if(data == null)
			throw new MappingNotFoundException(mcp);
		else return data.srgName;
	}

	/**
	 * Gets the MCP (deobfuscated) name of the class.
	 * Due to how it's implemented, it's considerably less efficient than its
	 * opposite operation.
	 * @param srg the SRG-obfuscated internal name of the desired class
	 * @return the MCP name of the class
	 */
	public String getMcpClass(String srg) {
		ObfuscationData data = getObfuscationData(srg);
		return data.mcpName;
	}

	/**
	 * Gets one between the SRG and MCP names.
	 * @param name the internal name of the desired class in either format
	 * @param obf whether it should return the obfuscated name
	 * @return a {@link String} containing the internal name of the class
	 * @throws MappingNotFoundException if no mapping is found
	 * @since 0.3.0
	 */
	public String mapClass(String name, boolean obf) {
		String srg;
		try {
			srg = this.getSrgClass(name);
		} catch(MappingNotFoundException e) {
			srg = name;
			name = this.getMcpClass(srg);
		}
		if(obf) return srg;
		else return name;
	}

	/**
	 * Gets the SRG-obfuscated name of a class member (field or method).
	 * The method signature must be in this format: "methodName methodDescriptor",
	 * with a space, because that's how it is in .tsrg files.
	 * @param mcpClass the MCP (deobfuscated) internal name of the container class
	 * @param member the field name or method signature
	 * @return the SRG name of the given member
	 * @throws MappingNotFoundException if no mapping is found
	 */
	public String getSrgMember(String mcpClass, String member) {
		ObfuscationData data = mapper.get(mcpClass);
		if(data == null)
			throw new MappingNotFoundException(mcpClass + "::" + member);
		return data.members.get(member);
	}

	/**
	 * Gets the MCP (deobfuscated) name of the given member.
	 * Due to how it's implemented, it's considerably less efficient than its
	 * opposite operation.
	 * @param srgClass the SRG-obfuscated internal name of the container class
	 * @param member the field name or method signature
	 * @return the MCP name of the given member
	 */
	public String getMcpMember(String srgClass, String member) {
		ObfuscationData data = getObfuscationData(srgClass);
		for(String mcp : data.members.keySet())
			if(data.members.get(mcp).equals(member))
				return mcp;
		return null;
	}

	/**
	 * Obfuscates or deobfuscates a member, given one of its names and the effective.
	 * @param className the internal or fully qualified name of the container class
	 * @param memberName the member of the class
	 * @param obf whether it should return the obfuscated name
	 * @return the mapped member name
	 * @throws MappingNotFoundException if no mapping is found
	 * @since 0.3.0
	 */
	public String mapMember(String className, String memberName, boolean obf) {
		className = className.replace('.', '/');
		String effectiveClassName = this.mapClass(className, obf);
		String srgMemberName;
		try {
			srgMemberName = this.getSrgMember(effectiveClassName, memberName);
		} catch(MappingNotFoundException e) {
			srgMemberName = memberName;
			memberName = this.getMcpMember(effectiveClassName, memberName);
		}
		if(obf) return srgMemberName;
		else return memberName;
	}

	/**
	 * Used internally. Gets the obfuscation data corresponding to the given SRG name.
	 * @return the desired {@link ObfuscationData} object
	 * @throws MappingNotFoundException if no {@link ObfuscationData} object is found
	 */
	private ObfuscationData getObfuscationData(String srg) {
		for(ObfuscationData s : mapper.values())
			if(s.srgName.equals(srg))
				return s;
		throw new MappingNotFoundException(srg);
	}

	/**
	 * Private class used internally for storing information about each
	 * class. It's private because there is no good reason anyone would
	 * want to access this outside of this class.
	 */
	private static class ObfuscationData {
		/**
		 * The MCP internal name (FQN with '/' instad of '.') of the class.
		 */
		private final String mcpName;

		/**
		 * The SRG internal name (FQN with '/' instad of '.') of the class.
		 */
		private final String srgName;

		/**
		 * A {@link Map} tying each member's deobfuscated name or signature to its
		 * SRG name.
		 */
		private final Map<String, String> members;


		/**
		 * The constructor. It takes in the line where the class is declared,
		 * which looks something like this:
		 * {@code internal/name/mcp internal/name/srg }
		 * @param s the String represeting the declaration line
		 */
		private ObfuscationData(String s) {
			String[] split = s.trim().split(" ");
			this.mcpName = split[0];
			this.srgName = split[1];
			this.members = new HashMap<>();
		}

		/**
		 * Adds a member to the target class. It takes in the line where the
		 * member is declared.
		 * For fields it looks like this:
		 * {@code fieldMcpName field_srg_name}
		 * For methods it looks like this:
		 * {@code methodName methodDescriptor method_srg_name}
		 * @param s the String representing the declaration line
		 */
		public void addMember(String s) {
			String[] split = s.trim().split(" ");
			if(split.length == 2) //field
				members.put(split[0], split[1]);
			else if (split.length == 3) //method
				members.put(split[0] + " " + split[1], split[2]);
		}
	}
}
