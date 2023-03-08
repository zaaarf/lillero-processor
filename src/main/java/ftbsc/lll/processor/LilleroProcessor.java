package ftbsc.lll.processor;

import com.squareup.javapoet.*;
import ftbsc.lll.IInjector;
import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.exceptions.MappingNotFoundException;
import ftbsc.lll.exceptions.TargetNotFoundException;
import ftbsc.lll.processor.annotations.*;
import ftbsc.lll.processor.tools.SrgMapper;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static ftbsc.lll.processor.tools.ASTUtils.*;

/**
 * The actual annotation processor behind the magic.
 * It (implicitly) implements the {@link Processor} interface by extending {@link AbstractProcessor}.
 */
@SupportedAnnotationTypes("ftbsc.lll.processor.annotations.Patch")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class LilleroProcessor extends AbstractProcessor {
	/**
	 * A {@link Set} of {@link String}s that will contain the fully qualified names
	 * of the generated injector files.
	 */
	private final Set<String> generatedInjectors = new HashSet<>();

	/**
	 * A static boolean that should be set to true when ran in a non-obfuscated environment.
	 */
	public static boolean obfuscatedEnvironment = false; //todo: set this

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
			if(annotation.getQualifiedName().toString().equals(Patch.class.getName())) {
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
				"Missing valid @Injector method in @Patch class " + elem + ", skipping.");
			return false;
		}
	}

	/**
	 * Finds the class name and maps it to the correct format.
	 * @param name the fully qualified name of the class to convert
	 * @param mapper the {@link SrgMapper} to use, may be null
	 * @implNote De facto, there is never any difference between the SRG and MCP name of a class.
	 *           In theory, differences only arise between SRG/MCP names and Notch (fully obfuscated)
	 *           names. However, this method still performs a conversion - just in case there is an
	 *           odd one out.
	 * @return the fully qualified class name
	 * @since 0.3.0
	 */
	private static String findClassName(String name, SrgMapper mapper) {
		return mapper == null ? name : mapper.mapClass(name, obfuscatedEnvironment).replace('/', '.');
	}

	/**
	 * Finds the class name and maps it to the correct format.
	 * @param patchAnn the {@link Patch} annotation containing target class info
	 * @param methodAnn the {@link FindMethod} annotation to fall back on, may be null
	 * @param mapper the {@link SrgMapper} to use, may be null
	 * @implNote De facto, there is never any difference between the SRG and MCP name of a class.
	 *           In theory, differences only arise between SRG/MCP names and Notch (fully obfuscated)
	 *           names. However, this method still performs a conversion - just in case there is an
	 *           odd one out.
	 * @return the fully qualified class name
	 * @since 0.3.0
	 */
	private static String findClassName(Patch patchAnn, FindMethod methodAnn, SrgMapper mapper) {
		String fullyQualifiedName =
			methodAnn == null || methodAnn.parent() == Object.class
				? getClassFullyQualifiedName(patchAnn.value())
				: getClassFullyQualifiedName(methodAnn.parent());
		return findClassName(fullyQualifiedName, mapper);
	}

	/**
	 * Finds the class name and maps it to the correct format.
	 * @param patchAnn the {@link Patch} annotation containing target class info
	 * @param mapper the {@link SrgMapper} to use, may be null
	 * @return the internal class name
	 * @since 0.3.0
	 */
	private static String findClassName(Patch patchAnn, SrgMapper mapper) {
		return findClassName(patchAnn, null, mapper);
	}

	/**
	 * Finds the member name and maps it to the correct format.
	 * @param parentFQN the already mapped FQN of the parent class
	 * @param memberName the name of the member
	 * @param mapper the {@link SrgMapper} to use, may be null
	 * @return the internal class name
	 * @since 0.3.0
	 */
	private static String findMemberName(String parentFQN, String memberName, SrgMapper mapper) {
		return mapper == null ? memberName : mapper.mapMember(parentFQN, memberName, obfuscatedEnvironment);
	}

	/**
	 * Finds the method name and maps it to the correct format.
	 * @param parentFQN the already mapped FQN of the parent class
	 * @param methodAnn the {@link FindMethod} annotation to fall back on, may be null
	 * @param stub the {@link ExecutableElement} for the stub
	 * @param mapper the {@link SrgMapper} to use, may be null
	 * @return the internal class name
	 * @since 0.3.0
	 */
	private static String findMethodName(String parentFQN, FindMethod methodAnn, ExecutableElement stub, SrgMapper mapper) {
		String methodName = methodAnn == null ? stub.getSimpleName().toString() : methodAnn.name();
		try {
			methodName = findMemberName(parentFQN, methodName, mapper);
		} catch(MappingNotFoundException e) {
			//not found: try again with the name of the annotated method
			if(methodAnn == null) {
				methodName = findMemberName(parentFQN, stub.getSimpleName().toString(), mapper);
			} else throw e;
		}
		return methodName;
	}

	/**
	 * Finds the method name and maps it to the correct format.
	 * @param patchAnn the {@link Patch} annotation containing target class info
	 * @param methodAnn the {@link FindMethod} annotation to fall back on, may be null
	 * @param stub the {@link ExecutableElement} for the stub
	 * @param mapper the {@link SrgMapper} to use
	 * @return the internal class name
	 * @since 0.3.0
	 */
	private static String findMethodName(Patch patchAnn, FindMethod methodAnn, ExecutableElement stub, SrgMapper mapper) {
		return findMethodName(findClassName(patchAnn, methodAnn, mapper), methodAnn, stub, mapper);
	}

	/**
	 * Finds a method given name, container and descriptor.
	 * @param fullyQualifiedNameParent the fully qualified name of the parent class of the method
	 * @param name the name to search for
	 * @param descr the descriptor to search for
	 * @param strict whether the search should be strict (see {@link Target#strict()} for more info)
	 * @return the desired method, if it exists
	 * @throws AmbiguousDefinitionException if it finds more than one candidate
	 * @throws TargetNotFoundException if it finds no valid candidate
	 * @since 0.3.0
	 */
	private ExecutableElement findMethod(String fullyQualifiedNameParent, String name, String descr, boolean strict) {
		TypeElement parent = processingEnv.getElementUtils().getTypeElement(fullyQualifiedNameParent);
		if(parent == null)
			throw new AmbiguousDefinitionException("Could not find parent class " + fullyQualifiedNameParent + "!");

		//try to find by name
		List<ExecutableElement> candidates = parent.getEnclosedElements()
			.stream()
			.filter(e -> e instanceof ExecutableElement)
			.map(e -> (ExecutableElement) e)
			.filter(e -> e.getSimpleName().contentEquals(name))
			.collect(Collectors.toList());
		if(candidates.size() == 0)
			throw new TargetNotFoundException(name + " " + descr);
		if(candidates.size() == 1 && !strict)
			return candidates.get(0);
		if(descr == null) {
			throw new AmbiguousDefinitionException(
				"Found " + candidates.size()
					+ " methods named " + name
					+ " in class " + fullyQualifiedNameParent + "!"
			);
		} else {
			candidates = candidates.stream()
				.filter(strict
					? c -> descr.equals(descriptorFromExecutableElement(c))
					: c -> descr.split("\\)")[0].equalsIgnoreCase(descriptorFromExecutableElement(c).split("\\)")[0])
				).collect(Collectors.toList());
			if(candidates.size() == 0)
				throw new TargetNotFoundException(name + " " + descr);
			if(candidates.size() > 1)
				throw new AmbiguousDefinitionException(
					"Found " + candidates.size()
						+ " methods named " + name
						+ " in class " + fullyQualifiedNameParent + "!"
				);
			return candidates.get(0);
		}
	}

	/**
	 * Finds the real method corresponding to a stub.
	 * @param stub the {@link ExecutableElement} for the stub
	 * @param mapper the {@link SrgMapper} to use
	 * @return the desired method, if it exists
	 * @throws AmbiguousDefinitionException if it finds more than one candidate
	 * @throws TargetNotFoundException if it finds no valid candidate
	 * @since 0.3.0
	 */
	private ExecutableElement findRealMethod(ExecutableElement stub, SrgMapper mapper) {
		Patch patchAnn = stub.getEnclosingElement().getAnnotation(Patch.class);
		FindMethod findAnn = stub.getAnnotation(FindMethod.class); //this may be null, it means no fallback info
		Target target = stub.getAnnotation(Target.class); //if this is null strict mode is always disabled
		String parentFQN = findClassName(patchAnn, findAnn, mapper);
		String methodName = findMethodName(patchAnn, findAnn, stub, mapper);
		return findMethod(
			parentFQN,
			methodName,
			descriptorFromExecutableElement(stub),
			target != null && target.strict());
	}

	/**
	 * Finds the real field corresponding to a stub.
	 * @param stub the {@link ExecutableElement} for the stub
	 * @param mapper the {@link SrgMapper} to use
	 * @return the desired method, if it exists
	 * @throws TargetNotFoundException if it finds no valid candidate
	 * @since 0.3.0
	 */
	private VariableElement findField(ExecutableElement stub, SrgMapper mapper) {
		Patch patchAnn = stub.getEnclosingElement().getAnnotation(Patch.class);
		FindField fieldAnn = stub.getAnnotation(FindField.class);
		String parentName = findClassName(getClassFullyQualifiedName(
			fieldAnn.parent().equals(Object.class)
				? patchAnn.value()
				: fieldAnn.parent()
		), mapper);
		String name = fieldAnn.name().equals("")
			? stub.getSimpleName().toString()
			: fieldAnn.name();
		TypeElement parent = processingEnv.getElementUtils().getTypeElement(parentName);
		List<VariableElement> candidates =
			parent.getEnclosedElements()
				.stream()
				.filter(f -> f instanceof VariableElement)
				.filter(f -> f.getSimpleName().contentEquals(name))
				.map(f -> (VariableElement) f)
				.collect(Collectors.toList());
		if(candidates.size() == 0)
			throw new TargetNotFoundException(stub.getSimpleName().toString());
		else return candidates.get(0); //there can only ever be one
	}

	/**
	 * Generates the Injector(s) contained in the given class.
	 * Basically implements the {@link IInjector} interface for you.
	 * @param cl the {@link TypeElement} for the given class
	 */
	private void generateInjectors(TypeElement cl) {
		SrgMapper mapper = null;
		try { //TODO: cant we get it from local?
			URL url = new URL("https://data.fantabos.co/output.tsrg");
			InputStream is = url.openStream();
			mapper = new SrgMapper(new BufferedReader(new InputStreamReader(is,
				StandardCharsets.UTF_8)).lines());
			is.close();
		} catch(IOException ignored) {} //TODO: proper handling

		//find class information
		Patch patchAnn = cl.getAnnotation(Patch.class);
		String targetClassSrgName = findClassName(patchAnn, mapper);

		//find package information
		Element packageElement = cl.getEnclosingElement();
		while (packageElement.getKind() != ElementKind.PACKAGE)
			packageElement = packageElement.getEnclosingElement();
		String packageName = packageElement.toString();

		//find injector(s) and target(s)
		List<ExecutableElement> injectors = findAnnotatedMethods(cl, MultipleInjectors.class);

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
			MultipleInjectors minjAnn = inj.getAnnotation(MultipleInjectors.class);
			int iterationNumber = 1;
			for(Injector injectorAnn : minjAnn.value()) { //java is dumb
				List<ExecutableElement> injectionCandidates = targets;

				//case 1: it has a name, try to match it
				if(!injectorAnn.targetName().equals("") && targetNames.contains(injectorAnn.targetName()))
					injectionCandidates =
						injectionCandidates
							.stream()
							.filter(i -> i.getSimpleName().toString().equals(injectorAnn.targetName()))
							.collect(Collectors.toList());

				//case 2: try to match by injectTargetName
				String inferredName = inj.getSimpleName()
					.toString()
					.replaceFirst("inject", "");
				injectionCandidates =
					injectionCandidates
						.stream()
						.filter(t -> t.getSimpleName().toString().equalsIgnoreCase(inferredName))
						.collect(Collectors.toList());

				//case 3: there is only one target
				if(targets.size() == 1)
					injectionCandidates.add(targets.get(0));

				ExecutableElement injectionTarget = null;

				if(injectionCandidates.size() == 1)
					injectionTarget = injectionCandidates.get(0);

				if(injectorAnn.params().length != 0) {
					StringBuilder descr = new StringBuilder("(");
					for(Class<?> p : injectorAnn.params())
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

				//if we haven't found it yet, it's an ambiguity
				if(injectionTarget == null)
					throw new AmbiguousDefinitionException("Unclear target for injector " + inj.getSimpleName().toString() + "!");
				else toGenerate.put(
						cl.getSimpleName().toString() + "Injector" + iterationNumber,
						new InjectorInfo(
							inj, findRealMethod(
								injectionTarget,
								mapper
							)
						)
					);
				iterationNumber++;
			}
		}

		//iterate over the map and generate the classes
		for(String injName : toGenerate.keySet()) {
			MethodSpec stubOverride = MethodSpec.overriding(toGenerate.get(injName).target)
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
				.addStatement("super." + toGenerate.get(injName).injector.getSimpleName() + "(clazz, main)", TypeName.get(cl.asType()))
				.build();

			TypeSpec injectorClass = TypeSpec.classBuilder(injName)
				.addModifiers(Modifier.PUBLIC)
				.superclass(cl.asType())
				.addSuperinterface(ClassName.get(IInjector.class))
				.addMethod(buildStringReturnMethod("name", cl.getSimpleName().toString()))
				.addMethod(buildStringReturnMethod("reason", patchAnn.reason()))
				.addMethod(buildStringReturnMethod("targetClass", targetClassSrgName.replace('/', '.')))
				.addMethod(buildStringReturnMethod("methodName", toGenerate.get(injName).target.getSimpleName().toString()))
				.addMethod(buildStringReturnMethod("methodDesc", descriptorFromExecutableElement(toGenerate.get(injName).target)))
				.addMethods(generateRequestedProxies(cl, mapper))
				.addMethod(stubOverride)
				.addMethod(inject)
				.build();

			JavaFile javaFile = JavaFile.builder(packageName, injectorClass).build();
			String injectorClassName = packageName + "." + injName;

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
	 * Builds a {@link MethodSpec} for a public method whose body simply returns a {@link String}.
	 * @param name the name of the method
	 * @param returnString the {@link String} to return
	 * @return the built {@link MethodSpec}
	 */
	private static MethodSpec buildStringReturnMethod(String name, String returnString) {
		return MethodSpec.methodBuilder(name)
			.addModifiers(Modifier.PUBLIC)
			.addAnnotation(Override.class)
			.returns(String.class)
			.addStatement("return $S", returnString)
			.build();
	}

	/**
	 * Finds any method annotated with {@link FindMethod} or {@link FindField} within the given
	 * class, and builds the {@link MethodSpec} necessary for building it.
	 * @param cl the class to search
	 * @return a {@link List} of method specs
	 * @since 0.2.0
	 */
	private List<MethodSpec> generateRequestedProxies(TypeElement cl, SrgMapper mapper) {
		List<MethodSpec> generated = new ArrayList<>();
		findAnnotatedMethods(cl, FindMethod.class)
			.stream()
			.filter(m -> !m.getModifiers().contains(Modifier.STATIC)) //skip static stuff as we can't override it
			.filter(m -> !m.getModifiers().contains(Modifier.FINAL)) //in case someone is trying to be funny
			.forEach(m -> {
				ExecutableElement targetMethod = findRealMethod(m, mapper);
				MethodSpec.Builder b = MethodSpec.overriding(m);
				b.addStatement("$T bd = $T.builder($S)",
					MethodProxy.Builder.class,
					MethodProxy.class,
					m.getSimpleName().toString()
				);
				b.addStatement("bd.setParent($S)", ((TypeElement) targetMethod.getEnclosingElement()).getQualifiedName().toString());
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
				VariableElement targetField = findField(m, mapper);
				MethodSpec.Builder b = MethodSpec.overriding(m);
				b.addStatement("$T bd = $T.builder($S)",
					FieldProxy.Builder.class,
					FieldProxy.class,
					targetField.getSimpleName().toString()
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
	private static class InjectorInfo {
		/**
		 * The {@link ExecutableElement} corresponding to the injector method.
		 */
		public final ExecutableElement injector;


		/**
		 * The {@link ExecutableElement} corresponding to the target method.
		 */
		public final ExecutableElement target;

		/**
		 * Public constructor.
		 * @param injector the injector {@link ExecutableElement}
		 * @param target the target {@link ExecutableElement}
		 */
		public InjectorInfo(ExecutableElement injector, ExecutableElement target) {
			this.injector = injector;
			this.target = target;
		}
	}
}