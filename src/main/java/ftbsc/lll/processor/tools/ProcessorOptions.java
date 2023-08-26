package ftbsc.lll.processor.tools;

import ftbsc.lll.IInjector;
import ftbsc.lll.mapper.IMapper;
import ftbsc.lll.mapper.MapperProvider;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class in charge of containing, parsing and processing all processor options,
 * from the simpler booleans to the more complicated mapper.
 */
public class ProcessorOptions {

	/**
	 * A {@link Set} of options currently supported by the processor.
	 */
	public static final Set<String> SUPPORTED = new HashSet<>(Arrays.asList(
		"mappingsFile", "anonymousClassWarning", "obfuscateInjectorMetadata",
		"noServiceProvider"
	));

	/**
	 * The environment the processor is acting in.
	 */
	public final ProcessingEnvironment env;

	/**
	 * The {@link IMapper} used to convert classes and variables
	 * to their obfuscated equivalent. Will be null when no mapper is in use.
	 */
	public final IMapper mapper;

	/**
	 * Whether the processor should issue warnings when compiling code anonymous
	 * classes which can't be checked for validity.
	 */
	public final boolean anonymousClassWarning;

	/**
	 * Whether injector metadata (what is returned by the functions of {@link IInjector})
	 * is to use obfuscated names instead of its normal names.
	 */
	public final boolean obfuscateInjectorMetadata;

	/**
	 * Whether the processor should skip the generation of the service provider.
	 */
	public final boolean noServiceProvider;

	/**
	 * The public constructor, parses and stores all given arguments.
	 * @param env the environment the processor is working in
	 */
	public ProcessorOptions(ProcessingEnvironment env) {
		this.env = env;
		String location = env.getOptions().get("mappingsFile");
		if(location != null) {
			List<String> lines = MapperProvider.fetchFromLocalOrRemote(location);
			this.mapper = MapperProvider.getMapper(lines);
			this.mapper.populate(lines, true);
		} else this.mapper = null;
		this.anonymousClassWarning = parseBooleanArg(env.getOptions().get("anonymousClassWarning"), true);
		this.obfuscateInjectorMetadata = parseBooleanArg(env.getOptions().get("obfuscateInjectorMetadata"), true);
		this.noServiceProvider = parseBooleanArg(env.getOptions().get("noServiceProvider"), false);
	}

	/**
	 * Parses a boolean arg from a String.
	 * @param arg the arg to parse
	 * @return the parsed boolean
	 */
	private static boolean parseBooleanArg(String arg, boolean defaultValue) {
		if(arg == null) return defaultValue;
		try { // 0 = false, any other integer = true
			int i = Integer.parseInt(arg);
			return i != 0;
		} catch(NumberFormatException ignored) {
			return Boolean.parseBoolean(arg);
		}
	}
}
