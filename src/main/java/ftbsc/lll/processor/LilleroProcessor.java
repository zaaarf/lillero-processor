package ftbsc.lll.processor;

import com.squareup.javapoet.*;
import ftbsc.lll.IInjector;
import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.exceptions.InvalidResourceException;
import ftbsc.lll.processor.annotations.Find;
import ftbsc.lll.processor.annotations.Injector;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.tools.containers.ClassContainer;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;
import ftbsc.lll.proxies.ProxyType;
import ftbsc.lll.proxies.impl.FieldProxy;
import ftbsc.lll.proxies.impl.MethodProxy;
import ftbsc.lll.proxies.impl.TypeProxy;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static ftbsc.lll.processor.tools.ASTUtils.*;
import static ftbsc.lll.processor.tools.JavaPoetUtils.*;

/**
 * The actual annotation processor behind the magic.
 * It (implicitly) implements the {@link Processor} interface by extending {@link AbstractProcessor}.
 */
@SupportedAnnotationTypes("ftbsc.lll.processor.annotations.Patch")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions({"mappingsFile", "badPracticeWarnings"})
public class LilleroProcessor extends AbstractProcessor {
	/**
	 * A {@link Set} of {@link String}s that will contain the fully qualified names
	 * of the generated injector files.
	 */
	private final Set<String> generatedInjectors = new HashSet<>();

	/**
	 * The {@link ObfuscationMapper} used to convert classes and variables
	 * to their obfuscated equivalent. Will be null when no mapper is in use.
	 */
	private ObfuscationMapper mapper;

	/**
	 * Whether the processor should issue warnings when compiling code adopting
	 * bad practices.
	 */
	public static boolean badPracticeWarnings = true;

	/**
	 * Initializes the processor with the processing environment by
	 * setting the {@code processingEnv} field to the value of the
	 * {@code processingEnv} argument.
	 * @param processingEnv environment to access facilities the tool framework
	 * provides to the processor
	 * @throws IllegalStateException if this method is called more than once.
	 * @since 0.3.0
	 */
	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		String location = processingEnv.getOptions().get("mappingsFile");
		if(location == null)
			mapper = null;
		else {
			InputStream targetStream;
			try {
				URI target = new URI(location);
				targetStream = target.toURL().openStream();
			} catch(URISyntaxException | IOException e) {
				//may be a local file path
				File f = new File(location);
				if(!f.exists())
					throw new InvalidResourceException(location);
				try {
					targetStream = new FileInputStream(f);
				} catch(FileNotFoundException ex) {
					throw new InvalidResourceException(location);
				}
			}
			//assuming its tsrg file
			//todo: replace crappy homebaked parser with actual library
			this.mapper = new ObfuscationMapper(new BufferedReader(new InputStreamReader(targetStream,
				StandardCharsets.UTF_8)).lines());
		}
		String warns = processingEnv.getOptions().get("badPracticeWarnings");
		if(warns == null)
			badPracticeWarnings = true;
		else {
			try { // 0 = false, any other integer = true
				int i = Integer.parseInt(warns);
				badPracticeWarnings = i != 0;
			} catch(NumberFormatException ignored) {
				badPracticeWarnings = Boolean.parseBoolean(warns);
			}
		}
	}

	/**
	 * Where the actual processing happens.
	 * It filters through whatever annotated class it's fed, and checks whether it contains
	 * the required information. It then generates injectors and a service provider for every
	 * remaining class.
	 * @see LilleroProcessor#isValidInjector(TypeElement)
	 * @param annotations the annotation types requested to be processed
	 * @param roundEnv environment for information about the current and prior round
	 * @return whether or not the set of annotation types are claimed by this processor
	 */
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		for (TypeElement annotation : annotations) {
			if(annotation.getQualifiedName().contentEquals(Patch.class.getName())) {
				Set<TypeElement> validInjectors =
					roundEnv.getElementsAnnotatedWith(annotation)
						.stream()
						.map(e -> (TypeElement) e)
						.filter(this::isValidInjector)
						.collect(Collectors.toSet());
				if(!validInjectors.isEmpty()) {
					validInjectors.forEach(this::generateClasses);
					if (!this.generatedInjectors.isEmpty()) {
						generateServiceProvider();
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * This checks whether a given class contains the requirements to be parsed into a Lillero injector.
	 * It must have at least one method annotated with {@link Target}, and one method annotated with {@link Injector}
	 * that must take in a ClassNode and MethodNode from ObjectWeb's ASM library.
	 * @param elem the element to check.
	 * @return whether it can be converted into a valid {@link IInjector}.
	 */
	private boolean isValidInjector(TypeElement elem) {
		TypeMirror classNodeType = processingEnv.getElementUtils().getTypeElement("org.objectweb.asm.tree.ClassNode").asType();
		TypeMirror methodNodeType = processingEnv.getElementUtils().getTypeElement("org.objectweb.asm.tree.MethodNode").asType();
		if (elem.getEnclosedElements().stream().anyMatch(e -> e.getAnnotation(Target.class) != null)
			&& elem.getEnclosedElements().stream().filter(e -> e instanceof ExecutableElement).anyMatch(e -> {
			List<? extends TypeMirror> params = ((ExecutableType) e.asType()).getParameterTypes();
			return e.getAnnotation(Injector.class) != null
				&& e.getAnnotation(Target.class) == null
				&& params.size() == 2
				&& processingEnv.getTypeUtils().isSameType(params.get(0), classNodeType)
				&& processingEnv.getTypeUtils().isSameType(params.get(1), methodNodeType);
		})) return true;
		else {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, //TODO orphan targets
				String.format("Missing valid @Injector method in @Patch class %s, skipping.", elem));
			return false;
		}
	}

	/**
	 * Generates the Injector(s) contained in the given class.
	 * Basically implements the {@link IInjector} interface for you.
	 * @param cl the {@link TypeElement} for the given class
	 */
	private void generateClasses(TypeElement cl) {
		//find class information
		Patch patchAnn = cl.getAnnotation(Patch.class);
		ClassContainer targetClass = new ClassContainer(
			getClassFullyQualifiedName(
				patchAnn,
				Patch::value,
				patchAnn.className()
			), this.processingEnv, this.mapper
		);

		//find package information
		Element packageElement = cl.getEnclosingElement();
		while (packageElement.getKind() != ElementKind.PACKAGE)
			packageElement = packageElement.getEnclosingElement();
		String packageName = packageElement.toString();

		//find annotated elements
		List<ExecutableElement> targets = findAnnotatedElement(cl, Target.class);
		List<ExecutableElement> injectors = findAnnotatedElement(cl, Injector.class);
		List<VariableElement> finders = findAnnotatedElement(cl, Find.class);

		//initialize the constructor builder
		MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder();

		List<VariableElement> methodFinders = new ArrayList<>();

		//take care of TypeProxies and FieldProxies first
		for(VariableElement proxyVar : finders) {
			ProxyType type = getProxyType(proxyVar);
			if(type == ProxyType.METHOD && proxyVar.getAnnotation(Find.class).name().equals("")) {
				//methods without a specified name will be handled later
				methodFinders.add(proxyVar);
				continue;
			}
			//case-specific handling
			if(type == ProxyType.TYPE) {
				//find and validate
				ClassContainer clazz = findClassOrFallback(targetClass, proxyVar.getAnnotation(Find.class), this.processingEnv, this.mapper);
				//types can be generated with a single instruction
				constructorBuilder.addStatement(
					"super.$L = $T.from($S, 0, $L)",
					proxyVar.getSimpleName().toString(),
					TypeProxy.class,
					clazz.fqnObf, //use obf name, at runtime it will be obfuscated
					mapModifiers(clazz.elem.getModifiers())
				);
			} else if(type == ProxyType.FIELD || type == ProxyType.METHOD)
				appendMemberFinderDefinition(targetClass, proxyVar, null, constructorBuilder, this.processingEnv, this.mapper);
		}

		//declare it once for efficiency
		List<String> injectorNames =
			injectors.stream()
				.map(ExecutableElement::getSimpleName)
				.map(Object::toString)
				.collect(Collectors.toList());

		//targets that have matched at least once are put in here
		Set<ExecutableElement> matchedTargets = new HashSet<>();

		//this will contain the classes to generate: the key is the class name
		HashMap<String, InjectorInfo> toGenerate = new HashMap<>();

		for(ExecutableElement tg : targets) {
			Target[] mtgAnn = tg.getAnnotationsByType(Target.class);
			int iterationNumber = 1;
			for(Target targetAnn : mtgAnn) {
				List<ExecutableElement> injectorCandidates = injectors;
				List<VariableElement> finderCandidates = methodFinders;

				if(!targetAnn.of().equals("")) {
					//case 1: find target by name
					injectorCandidates =
						injectorCandidates
							.stream()
							.filter(i -> i.getSimpleName().contentEquals(targetAnn.of()))
							.collect(Collectors.toList());
					finderCandidates =
						finderCandidates
							.stream()
							.filter(i -> i.getSimpleName().contentEquals(targetAnn.of()))
							.collect(Collectors.toList());
				} else if(injectors.size() == 1) {
					//case 2: there is only one injector
					finderCandidates = new ArrayList<>(); //no candidates
					injectorCandidates = new ArrayList<>();
					injectorCandidates.add(injectors.get(0));
				} else {  //todo match finders based on same name
					//case 3: try to match by injectTargetName
					finderCandidates = new ArrayList<>(); //no candidates
					String inferredName = "inject" + tg.getSimpleName();
					injectorCandidates =
						injectorCandidates
							.stream()
							.filter(t -> t.getSimpleName().toString().equalsIgnoreCase(inferredName))
							.collect(Collectors.toList());
				}

				//throw exception if user is a moron and defined a finder and an injector with the same name
				if(finderCandidates.size() != 0 && injectorCandidates.size() != 0)
					throw new AmbiguousDefinitionException(
						String.format("Target specified user %s, but name was used by both a finder and injector.", targetAnn.of())
					);
				else if(finderCandidates.size() == 0 && injectorCandidates.size() != 1)
					throw new AmbiguousDefinitionException(
						String.format("Found multiple candidate injectors for target %s::%s!", cl.getSimpleName(), tg.getSimpleName())
					);
				else if(injectorCandidates.size() == 0 && finderCandidates.size() != 1)
					throw new AmbiguousDefinitionException(
						String.format("Found multiple candidate finders for target %s::%s!", cl.getSimpleName(), tg.getSimpleName())
					);
				else {
					if(injectorCandidates.size() == 1) {
						//matched an injector!
						toGenerate.put(
							String.format("%sInjector%d", cl.getSimpleName(), iterationNumber),
							new InjectorInfo(injectorCandidates.get(0), tg)
						);
						iterationNumber++; //increment is only used by injectors
					} else {
						//matched a finder!
						appendMemberFinderDefinition(
							targetClass,
							finderCandidates.get(0),
							tg,
							constructorBuilder,
							this.processingEnv,
							this.mapper
						);
					}
				}
			}
		}

		//iterate over the map and generate the classes
		for(String injName : toGenerate.keySet()) {
			String targetMethodDescriptor = descriptorFromExecutableElement(toGenerate.get(injName).target);
			String targetMethodName = findMemberName(targetClass.fqnObf, toGenerate.get(injName).target.getSimpleName().toString(), targetMethodDescriptor, this.mapper);
			MethodSpec inject = MethodSpec.methodBuilder("inject")
				.addModifiers(Modifier.PUBLIC)
				.returns(void.class)
				.addAnnotation(Override.class)
				.addParameter(ParameterSpec.builder(
					TypeName.get(processingEnv
						.getElementUtils()
						.getTypeElement("org.objectweb.asm.tree.ClassNode").asType()), "clazz").build())
				.addParameter(ParameterSpec.builder(
					TypeName.get(processingEnv
						.getElementUtils()
						.getTypeElement("org.objectweb.asm.tree.MethodNode").asType()), "main").build())
				.addStatement(String.format("super.%s(clazz, main)", toGenerate.get(injName).injector.getSimpleName()), TypeName.get(cl.asType()))
				.build();

			TypeSpec injectorClass = TypeSpec.classBuilder(injName)
				.addModifiers(Modifier.PUBLIC)
				.superclass(cl.asType())
				.addSuperinterface(ClassName.get(IInjector.class))
				.addMethod(constructorBuilder.build())
				.addMethod(buildStringReturnMethod("name", cl.getSimpleName().toString()))
				.addMethod(buildStringReturnMethod("reason", toGenerate.get(injName).reason))
				.addMethod(buildStringReturnMethod("targetClass", targetClass.fqn))
				.addMethod(buildStringReturnMethod("methodName", targetMethodName))
				.addMethod(buildStringReturnMethod("methodDesc", targetMethodDescriptor))
				.addMethods(generateDummies(targets))
				.addMethod(inject)
				.build();

			JavaFile javaFile = JavaFile.builder(packageName, injectorClass).build();
			String injectorClassName = String.format("%s.%s", packageName, injName);

			try {
				JavaFileObject injectorFile = processingEnv.getFiler().createSourceFile(injectorClassName);
				PrintWriter out = new PrintWriter(injectorFile.openWriter());
				javaFile.writeTo(out);
				out.close();
			} catch(IOException e) {
				throw new RuntimeException(e);
			}

			this.generatedInjectors.add(injectorClassName);
		}
	}

	/**
	 * Generates the Service Provider file for the generated injectors.
	 */
	private void generateServiceProvider() {
		try {
			FileObject serviceProvider =
				processingEnv.getFiler().createResource(
					StandardLocation.CLASS_OUTPUT, "", "META-INF/services/ftbsc.lll.IInjector"
				);
			PrintWriter out = new PrintWriter(serviceProvider.openWriter());
			this.generatedInjectors.forEach(out::println);
			out.close();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Container for information about a class that is to be generated.
	 * Only used internally.
	 */
	private class InjectorInfo {
		/**
		 * The {@link ExecutableElement} corresponding to the injector method.
		 */
		public final ExecutableElement injector;

		/**
		 * The {@link ExecutableElement} corresponding to the target method stub.
		 */
		public final ExecutableElement targetStub;

		/**
		 * The reason for the injection.
		 */
		public final String reason;

		/**
		 * The {@link ExecutableElement} corresponding to the target method.
		 */
		private final ExecutableElement target;

		/**
		 * Public constructor.
		 * @param injector the injector {@link ExecutableElement}
		 * @param targetStub the target {@link ExecutableElement}
		 */
		public InjectorInfo(ExecutableElement injector, ExecutableElement targetStub) {
			this.injector = injector;
			this.targetStub = targetStub;
			this.reason = injector.getAnnotation(Injector.class).reason();
			this.target = findMethodFromStub(targetStub, null, processingEnv);
		}
	}
}