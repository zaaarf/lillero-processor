package ftbsc.lll.processor;

import ftbsc.lll.IInjector;
import ftbsc.lll.processor.utils.Mapper;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitableMappingTree;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

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
		"mappingsNamespaceFrom",
		"mappingsNamespaceTo",
		"anonymousClassWarning",
		"manualClassWarning",
		"obfuscateInjectorMetadata",
		"noServiceProvider",
		"fakeMixin", // lillero-mixin support
		"outputPackage", // mostly for lillero-mixin, but other use cases may exist
		"apiPackage"
	));

	/**
	 * The environment the processor is acting in.
	 */
	public final ProcessingEnvironment env;

	/**
	 * The {@link Mapper} used to convert classes and variables to their obfuscated equivalent.
	 * Will perform no-ops when no mappings were provided.
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

	/**f
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
	 * The package that lillero expects to find its API in.
	 * Note that internal package consistency is expected to be guaranteed.
	 * Will default to the known one.
	 */
	public final String apiPackage;

	/**
	 * The public constructor, parses and stores all given arguments.
	 * @param env the environment the processor is working in
	 */
	public ProcessorOptions(ProcessingEnvironment env) {
		this.env = env;
		this.anonymousClassWarning = parseBooleanArg(env.getOptions().get("anonymousClassWarning"), true);
		this.manualClassWarning = parseBooleanArg(env.getOptions().get("manualClassWarning"), true);
		this.obfuscateInjectorMetadata = parseBooleanArg(env.getOptions().get("obfuscateInjectorMetadata"), true);
		this.noServiceProvider = parseBooleanArg(env.getOptions().get("noServiceProvider"), false);
		this.fakeMixin = env.getOptions().get("fakeMixin");
		this.outputPackage = env.getOptions().get("outputPackage");
		this.apiPackage = env.getOptions().getOrDefault("apiPackage", "ftbsc.lll");

		String location = env.getOptions().get("mappingsFile");
		String namespaceFrom = env.getOptions().get("mappingsNamespaceFrom");
		String namespaceTo = env.getOptions().get("mappingsNamespaceTo");

		VisitableMappingTree tree = new MemoryMappingTree();
		if(location != null) {
			try {
				readMappingsFromLocalOrRemote(tree, location);

				if(namespaceFrom != null && tree.getNamespaceId(namespaceFrom) == MappingTreeView.NULL_NAMESPACE_ID) {
					env.getMessager().printMessage(Diagnostic.Kind.ERROR, "\"namespaceFrom\" not found in the given mappings!");
				}

				if(namespaceTo != null && tree.getNamespaceId(namespaceTo) == MappingTreeView.NULL_NAMESPACE_ID) {
					env.getMessager().printMessage(Diagnostic.Kind.ERROR, "\"namespaceTo\" not found in the given mappings!");
				}

				if(namespaceTo == null && namespaceFrom == null && tree.getDstNamespaces().size() != 1) {
					env.getMessager().printMessage(Diagnostic.Kind.ERROR, "The given mapping format requires specifying namespaces.");
				}
			} catch(IOException ex) {
				env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to read mappings: " + ex.getMessage());
			}
		}

		this.mapper = new Mapper(tree, namespaceFrom, namespaceTo);
	}

	private static void readMappingsFromLocalOrRemote(VisitableMappingTree tree, String location) throws IOException {
		InputStream targetStream;
		try {
			URI target = new URI(location);
			targetStream = target.toURL().openStream();
		} catch(URISyntaxException | IllegalArgumentException ex) {
			// may be a local file path
			File f = new File(location);
			targetStream = new FileInputStream(f);
		}

		// this is ugly but fabric loves their readers
		String body = new BufferedReader(new InputStreamReader(targetStream, StandardCharsets.UTF_8))
			.lines()
			.collect(Collectors.joining("\n"));

		MappingReader.read(
			new StringReader(body),
			MappingReader.detectFormat(new StringReader(body)),
			tree
		);
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
