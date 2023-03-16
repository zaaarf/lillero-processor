package ftbsc.lll.processor;

import com.squareup.javapoet.*;
import ftbsc.lll.IInjector;
import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.exceptions.InvalidResourceException;
import ftbsc.lll.processor.annotations.*;
import ftbsc.lll.processor.tools.obfuscation.ObfuscationMapper;
import ftbsc.lll.proxies.FieldProxy;
import ftbsc.lll.proxies.MethodProxy;

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
import static ftbsc.lll.processor.tools.ASTUtils.getClassFullyQualifiedName;
import static ftbsc.lll.processor.tools.JavaPoetUtils.*;

/**
 * The actual annotation processor behind the magic.
 * It (implicitly) implements the {@link Processor} interface by extending {@link AbstractProcessor}.
 */
@SupportedAnnotationTypes("ftbsc.lll.processor.annotations.Patch")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions("mappingsFile")
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
					validInjectors.forEach(this::generateInjectors);
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
			&& elem.getEnclosedElements().stream().anyMatch(e -> {
			List<? extends TypeMirror> params = ((ExecutableType) e.asType()).getParameterTypes();
			return e.getAnnotation(Injector.class) != null
				&& e.getAnnotation(Target.class) == null
				&& params.size() == 2
				&& processingEnv.getTypeUtils().isSameType(params.get(0), classNodeType)
				&& processingEnv.getTypeUtils().isSameType(params.get(1), methodNodeType);
		})) return true;
		else {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
				String.format("Missing valid @Injector method in @Patch class %s, skipping.", elem));
			return false;
		}
	}

	/**
	 * Generates the Injector(s) contained in the given class.
	 * Basically implements the {@link IInjector} interface for you.
	 * @param cl the {@link TypeElement} for the given class
	 */
	private void generateInjectors(TypeElement cl) {
		//find class information
		Patch patchAnn = cl.getAnnotation(Patch.class);
		String targetClassFQN =
			findClassName(getClassFullyQualifiedName(patchAnn, Patch::value), this.mapper)
				.replace('/', '.');

		//find package information
		Element packageElement = cl.getEnclosingElement();
		while (packageElement.getKind() != ElementKind.PACKAGE)
			packageElement = packageElement.getEnclosingElement();
		String packageName = packageElement.toString();

		//find injector(s) and target(s)
		List<ExecutableElement> injectors = findAnnotatedMethods(cl, Injector.class);

		List<ExecutableElement> targets = findAnnotatedMethods(cl, Target.class);

		//declare it once for efficiency
		List<String> targetNames =
			targets.stream()
				.map(ExecutableElement::getSimpleName)
				.map(Object::toString)
				.collect(Collectors.toList());

		//this will contain the classes to generate: the key is the class name
		Map<String, InjectorInfo> toGenerate = new HashMap<>();

		for(ExecutableElement inj : injectors) {
			Injector[] minjAnn = inj.getAnnotationsByType(Injector.class);
			int iterationNumber = 1;
			for(Injector injectorAnn : minjAnn) { //java is dumb
				List<ExecutableElement> injectionCandidates = targets;

				if(!injectorAnn.targetName().equals("") && targetNames.contains(injectorAnn.targetName())) {
					//case 1: it has a name, try to match it
					injectionCandidates =
						injectionCandidates
							.stream()
							.filter(i -> i.getSimpleName().contentEquals(injectorAnn.targetName()))
							.collect(Collectors.toList());
				} else if(targets.size() == 1) {
					//case 2: there is only one target
					injectionCandidates = new ArrayList<>();
					injectionCandidates.add(targets.get(0));
				} else {
					//case 3: try to match by injectTargetName
					String inferredName = inj.getSimpleName()
						.toString()
						.replaceFirst("inject", "");
					injectionCandidates =
						injectionCandidates
							.stream()
							.filter(t -> t.getSimpleName().toString().equalsIgnoreCase(inferredName))
							.collect(Collectors.toList());
				}

				ExecutableElement injectionTarget = null;

				if(injectionCandidates.size() == 1)
					injectionTarget = injectionCandidates.get(0);

				else {
					List<TypeMirror> params = classArrayFromAnnotation(injectorAnn, Injector::params, processingEnv.getElementUtils());

					if(params.size() != 0) {
						StringBuilder descr = new StringBuilder("(");
						for(TypeMirror p : params)
							descr.append(descriptorFromType(TypeName.get(p)));
						descr.append(")");
						injectionCandidates =
							injectionCandidates
								.stream()
								.filter(t -> //we care about arguments but not really about return type
									descr.toString()
										.split("\\)")[0]
										.equalsIgnoreCase(descriptorFromExecutableElement(t).split("\\)")[0])
								).collect(Collectors.toList());
					}

					if(injectionCandidates.size() == 1)
						injectionTarget = injectionCandidates.get(0);
				}

				//if we haven't found it yet, it's an ambiguity
				if(injectionTarget == null)
					throw new AmbiguousDefinitionException(String.format("Unclear target for injector %s::%s!", cl.getSimpleName(), inj.getSimpleName()));
				else toGenerate.put(
						String.format("%sInjector%d", cl.getSimpleName(), iterationNumber),
						new InjectorInfo(inj, injectionTarget)
					);
				iterationNumber++;
			}
		}

		//iterate over the map and generate the classes
		for(String injName : toGenerate.keySet()) {
			String targetMethodDescriptor = descriptorFromExecutableElement(toGenerate.get(injName).target);
			String targetMethodName = findMemberName(targetClassFQN, toGenerate.get(injName).target.getSimpleName().toString(), targetMethodDescriptor, this.mapper);

			MethodSpec stubOverride = MethodSpec.overriding(toGenerate.get(injName).targetStub)
				.addStatement("throw new $T($S)", RuntimeException.class, "This is a stub and should not have been called")
				.build();

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
				.addMethod(buildStringReturnMethod("name", cl.getSimpleName().toString()))
				.addMethod(buildStringReturnMethod("reason", patchAnn.reason()))
				.addMethod(buildStringReturnMethod("targetClass", targetClassFQN))
				.addMethod(buildStringReturnMethod("methodName", targetMethodName))
				.addMethod(buildStringReturnMethod("methodDesc", targetMethodDescriptor))
				.addMethods(generateRequestedProxies(cl, this.mapper))
				.addMethod(stubOverride)
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
	 * Finds any method annotated with {@link FindMethod} or {@link FindField} within the given
	 * class, and builds the {@link MethodSpec} necessary for building it.
	 * @param cl the class to search
	 * @return a {@link List} of method specs
	 * @since 0.2.0
	 */
	private List<MethodSpec> generateRequestedProxies(TypeElement cl, ObfuscationMapper mapper) {
		List<MethodSpec> generated = new ArrayList<>();
		findAnnotatedMethods(cl, FindMethod.class)
			.stream()
			.filter(m -> !m.getModifiers().contains(Modifier.STATIC)) //skip static stuff as we can't override it
			.filter(m -> !m.getModifiers().contains(Modifier.FINAL)) //in case someone is trying to be funny
			.forEach(m -> {
				ExecutableElement targetMethod = (ExecutableElement) findMemberFromStub(m, processingEnv);
				MethodSpec.Builder b = MethodSpec.overriding(m);

				String targetParentFQN = findClassName(((TypeElement) targetMethod.getEnclosingElement()).getQualifiedName().toString(), mapper);

				b.addStatement("$T bd = $T.builder($S)",
					MethodProxy.Builder.class,
					MethodProxy.class,
					findMemberName(targetParentFQN, targetMethod.getSimpleName().toString(), descriptorFromExecutableElement(targetMethod), mapper)
				);

				b.addStatement("bd.setParent($S)", targetParentFQN);

				for(Modifier mod : targetMethod.getModifiers())
					b.addStatement("bd.addModifier($L)", mapModifier(mod));

				for(TypeParameterElement p : targetMethod.getTypeParameters())
					b.addStatement("bd.addParameter($T.class)", p.asType());

				b.addStatement("bd.setReturnType($T.class)", targetMethod.getReturnType());
				b.addStatement("return bd.build()");

				generated.add(b.build());
			});
		findAnnotatedMethods(cl, FindField.class)
			.stream()
			.filter(m -> !m.getModifiers().contains(Modifier.STATIC))
			.filter(m -> !m.getModifiers().contains(Modifier.FINAL))
			.forEach(m -> {
				VariableElement targetField = (VariableElement) findMemberFromStub(m, processingEnv);
				MethodSpec.Builder b = MethodSpec.overriding(m);

				String targetParentFQN = findClassName(((TypeElement) targetField.getEnclosingElement()).getQualifiedName().toString(), mapper);

				b.addStatement("$T bd = $T.builder($S)",
					FieldProxy.Builder.class,
					FieldProxy.class,
					findMemberName(targetParentFQN, targetField.getSimpleName().toString(), null, mapper)
				);

				b.addStatement("bd.setParent($S)", ((TypeElement) targetField.getEnclosingElement()).getQualifiedName().toString());

				for(Modifier mod : targetField.getModifiers())
					b.addStatement("bd.addModifier($L)", mapModifier(mod));

				b.addStatement("bd.setType($T.class)", targetField.asType());
				b.addStatement("return bd.build()");

				generated.add(b.build());
			});
		return generated;
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
			this.target = (ExecutableElement) findMemberFromStub(targetStub, processingEnv);
		}
	}
}