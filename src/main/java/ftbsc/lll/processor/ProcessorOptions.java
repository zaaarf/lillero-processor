package ftbsc.lll.processor;

import ftbsc.lll.IInjector;
import ftbsc.lll.mapper.MapperProvider;
import ftbsc.lll.mapper.utils.Mapper;

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
		"mappingsFile",
		"anonymousClassWarning",
		"manualClassWarning",
		"obfuscateInjectorMetadata",
		"noServiceProvider",
		"fakeMixin", // lillero-mixin support
		"outputPackage" // mostly for lillero-mixin, but other use cases may exist
	));

	/**
	 * The environment the processor is acting in.
	 */
	public final ProcessingEnvironment env;

	/**
	 * The {@link Mapper} used to convert classes and variables
	 * to their obfuscated equivalent. Will be null when no mapper is in use.
	 */
	public final Mapper mapper;

	/**
	 * Whether the processor should issue a warning when generating for an anonymous
	 * class which can't be checked for validity.
	 */
	public final boolean anonymousClassWarning;

	/**
	 * Whether the processor should issue a warning when a manually specified fully
	 * qualified name can't be checked for validity.
	 */
	public final boolean manualClassWarning;

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
	 * The fully qualified name of the fake mixin class to generate.
	 * No fake mixin class will be generated when this is null.
	 */
	public final String fakeMixin;

	/**
	 * The package where the generated injectors will be put.
	 * When this is null, the patches will be put in the same as the annotated class.
	 */
	public final String outputPackage;

	/**
	 * The public constructor, parses and stores all given arguments.
	 * @param env the environment the processor is working in
	 */
	public ProcessorOptions(ProcessingEnvironment env) {
		this.env = env;
		String location = env.getOptions().get("mappingsFile");
		if(location != null) {
			List<String> lines = MapperProvider.fetchFromLocalOrRemote(location);
			this.mapper = MapperProvider.getMapper(lines).getMapper(lines, true);
		} else this.mapper = null;
		this.anonymousClassWarning = parseBooleanArg(env.getOptions().get("anonymousClassWarning"), true);
		this.manualClassWarning = parseBooleanArg(env.getOptions().get("manualClassWarning"), true);
		this.obfuscateInjectorMetadata = parseBooleanArg(env.getOptions().get("obfuscateInjectorMetadata"), true);
		this.noServiceProvider = parseBooleanArg(env.getOptions().get("noServiceProvider"), false);
		this.fakeMixin = env.getOptions().get("fakeMixin");
		this.outputPackage = env.getOptions().get("outputPackage");
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
