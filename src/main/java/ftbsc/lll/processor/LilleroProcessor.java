package ftbsc.lll.processor;

import com.squareup.javapoet.*;
import ftbsc.lll.IInjector;
import ftbsc.lll.exceptions.AmbiguousDefinitionException;
import ftbsc.lll.exceptions.OrphanElementException;
import ftbsc.lll.processor.annotations.*;
import ftbsc.lll.processor.containers.ClassContainer;
import ftbsc.lll.processor.containers.InjectorInfo;
import ftbsc.lll.processor.containers.MethodContainer;
import ftbsc.lll.proxies.ProxyType;
import ftbsc.lll.proxies.impl.TypeProxy;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import static ftbsc.lll.processor.utils.ASTUtils.*;
import static ftbsc.lll.processor.utils.JavaPoetUtils.*;

/**
 * The actual annotation processor behind the magic.
 * It (implicitly) implements the {@link Processor} interface by extending {@link AbstractProcessor}.
 */
@SupportedAnnotationTypes({"ftbsc.lll.processor.annotations.Patch", "ftbsc.lll.processor.annotations.BareInjector"})
public class LilleroProcessor extends AbstractProcessor {
	/**
	 * A {@link Set} of {@link String}s that will contain the fully qualified names
	 * of the generated injector files.
	 */
	private final Set<String> injectors = new HashSet<>();

	/**
	 * A {@link Set} of {@link ClassName}s representing the classes that
	 * are being targeted.
	 */
	private final Set<ClassName> targets = new HashSet<>();

	/**
	 * An object representing the various options passed to the processor.
	 */
	private ProcessorOptions options = null;

	/**
	 * Method overriding default implementation to manually pass supported options.
	 * @return a {@link Set} of options supported by this processor.
	 */
	@Override
	public Set<String> getSupportedOptions() {
		return ProcessorOptions.SUPPORTED;
	}

	/**
	 * Always returns the latest version since this should never break.
	 * @return the latest version
	 */
	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latest();
	}

	/**
	 * Returns the {@link ProcessorOptions} for this instance, creating the object if
	 * it hasn't been already.
	 * @return the {@link ProcessorOptions} for this instance
	 */
	public ProcessorOptions getProcessorOptions() {
		if(this.options == null) this.options = new ProcessorOptions(this.processingEnv);
		return this.options;
	}

	/**
	 * Where the actual processing happens.
	 * It filters through whatever annotated class it's fed, and checks whether it contains
	 * the required information. It then generates injectors and a service provider for every
	 * remaining class.
	 * @see LilleroProcessor#isValidInjector(TypeElement)
	 * @param annotations the annotation types requested to be processed
	 * @param roundEnv environment for information about the current and prior round
	 * @return whether the set of annotation types are claimed by this processor
	 */
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		ProcessorOptions options = this.getProcessorOptions();
		for(TypeElement annotation : annotations) {
			if(annotation.getQualifiedName().contentEquals(Patch.class.getName())) {
				for(Element e : roundEnv.getElementsAnnotatedWith(annotation)) {
					TypeElement type = (TypeElement) e;
					if(this.isValidInjector(type)) {
						this.generateClasses(type);
						if(options.fakeMixin != null) {
							this.markClassAsTarget(type);
						}
					} else if(options.fakeMixin != null && e.getAnnotation(BareInjector.class) != null) {
						this.markClassAsTarget(type);
					}
				}
			} else if(annotation.getQualifiedName().contentEquals(BareInjector.class.getName())) {
				TypeMirror injectorType = this.processingEnv.getElementUtils().getTypeElement("ftbsc.lll.IInjector").asType();
				for(Element e : roundEnv.getElementsAnnotatedWith(annotation)) {
					TypeElement type = (TypeElement) e;
					if(this.processingEnv.getTypeUtils().isAssignable(e.asType(), injectorType)) {
						this.injectors.add(type.getQualifiedName().toString());
					} else {
						this.processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, String.format(
							"Class %s annotated with @BareInjector is not an instance of IInjector, skipping...",
							type.getQualifiedName().toString()
						));
					}
				}
			}
		}

		if(options.fakeMixin != null && !this.injectors.isEmpty()) {
			this.generateFakeMixinClass(options.fakeMixin);
		}

		if(!options.noServiceProvider && !this.injectors.isEmpty()) {
			this.generateServiceProvider();
			return true;
		} else return false;
	}

	/**
	 * This checks whether a given class contains the requirements to be parsed into a Lillero injector.
	 * It must have at least one method annotated with {@link Target}, and one method annotated with {@link Injector}
	 * that must take in either a ClassNode and MethodNode (from ObjectWeb's ASM library) or only a MethodNode.
	 * @param elem the element to check.
	 * @return whether it can be converted into a valid {@link IInjector}.
	 */
	private boolean isValidInjector(TypeElement elem) {
		TypeMirror classNodeType = this.processingEnv.getElementUtils().getTypeElement("org.objectweb.asm.tree.ClassNode").asType();
		TypeMirror methodNodeType = this.processingEnv.getElementUtils().getTypeElement("org.objectweb.asm.tree.MethodNode").asType();
		if(elem.getEnclosedElements().stream().anyMatch(e -> e.getAnnotation(Target.class) != null)
			&& elem.getEnclosedElements().stream().filter(e -> e instanceof ExecutableElement).anyMatch(e -> {
			List<? extends TypeMirror> params = ((ExecutableType) e.asType()).getParameterTypes();
			return e.getAnnotation(Injector.class) != null
				&& e.getAnnotation(Target.class) == null
				&& (
					(params.size() == 2
						&& this.processingEnv.getTypeUtils().isSameType(params.get(0), classNodeType)
						&& this.processingEnv.getTypeUtils().isSameType(params.get(1), methodNodeType)
					) || (params.size() == 1 && this.processingEnv.getTypeUtils().isSameType(params.get(0), methodNodeType))
			);
		})) return true;
		else {
			this.processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
				String.format("Missing valid @Injector method in @Patch class %s, skipping.", elem));
			return false;
		}
	}

	/**
	 * Marks the given class as a "target" for purposes of generating the fake mixin.
	 * @param type the class in question
	 */
	private void markClassAsTarget(TypeElement type) {
		Patch ann = type.getAnnotation(Patch.class);
		TypeMirror value = getTypeFromAnnotation(ann, Patch::value, this.processingEnv);
		ClassName name = ClassName.get((TypeElement) this.processingEnv.getTypeUtils().asElement(value));
		for(String inner : ann.inner()) {
			name = name.nestedClass(inner);
		}
		this.targets.add(name);
	}

	/**
	 * Generates the Injector(s) contained in the given class.
	 * Basically implements the {@link IInjector} interface for you.
	 * @param cl the {@link TypeElement} for the given class
	 */
	private void generateClasses(TypeElement cl) {
		//find class information
		Patch patchAnn = cl.getAnnotation(Patch.class);
		ClassContainer targetClass = ClassContainer.from(
			patchAnn, Patch::value, patchAnn.inner(), this.getProcessorOptions()
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
		constructorBuilder.addModifiers(Modifier.PUBLIC);

		List<VariableElement> methodFinders = new ArrayList<>();

		//take care of TypeProxies and FieldProxies first
		for(VariableElement proxyVar : finders) {
			ProxyType type = getProxyType(proxyVar);
			if(type == ProxyType.METHOD && proxyVar.getAnnotation(Find.class).name().isEmpty()) {
				//methods without a specified name will be handled later
				methodFinders.add(proxyVar);
				continue;
			}
			//case-specific handling
			if(type == ProxyType.TYPE) {
				//find and validate
				ClassContainer clazz = ClassContainer.findOrFallback(
					ClassContainer.from(cl, this.getProcessorOptions()),
					patchAnn,
					proxyVar.getAnnotation(Find.class),
					this.getProcessorOptions()
				);
				//types can be generated with a single instruction
				constructorBuilder.addStatement(
					"super.$L = $T.from($S, 0, $L)",
					proxyVar.getSimpleName().toString(),
					TypeProxy.class,
					clazz.data.nameMapped.replace('/', '.'), //use obf name, at runtime it will be obfuscated
					clazz.elem == null ? 0 : mapModifiers(clazz.elem.getModifiers())
				);
			} else if(type == ProxyType.FIELD)
				appendMemberFinderDefinition(
					proxyVar,
					null,
					null,
					constructorBuilder,
					this.getProcessorOptions()
				);
		}

		//this will contain the classes to generate: the key is the class name
		HashMap<String, InjectorInfo> toGenerate = new HashMap<>();

		//these are needed for orphan checks
		HashSet<ExecutableElement> matchedInjectors = new HashSet<>();
		HashSet<VariableElement> matchedMethodFinders = new HashSet<>();

		int iterationNumber = 1;
		for(ExecutableElement tg : targets) {
			Target[] mtgAnn = tg.getAnnotationsByType(Target.class);
			for(Target targetAnn : mtgAnn) {
				List<ExecutableElement> injectorCandidates = injectors;
				List<VariableElement> finderCandidates = methodFinders;

				//find target by name
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

				//throw exception if user is a moron and defined a finder and an injector with the same name
				if(!finderCandidates.isEmpty() && !injectorCandidates.isEmpty())
					throw new AmbiguousDefinitionException(
						String.format("Target specified user %s, but name was used by both a finder and injector.", targetAnn.of())
					);
				else if(finderCandidates.isEmpty() && injectorCandidates.isEmpty())
					processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
						String.format(
							"Found orphan @Target annotation on method %s.%s pointing at method %s, it will be ignored!",
							cl.getSimpleName().toString(),
							tg.getSimpleName().toString(),
							targetAnn.of()
						)
					);
				else if(finderCandidates.isEmpty() && injectorCandidates.size() != 1)
					throw new AmbiguousDefinitionException(
						String.format("Found multiple candidate injectors for target %s::%s!", cl.getSimpleName(), tg.getSimpleName())
					);
				else if(injectorCandidates.isEmpty() && finderCandidates.size() != 1)
					throw new AmbiguousDefinitionException(
						String.format("Found multiple candidate finders for target %s::%s!", cl.getSimpleName(), tg.getSimpleName())
					);
				else {
					if(injectorCandidates.size() == 1) {
						//matched an injector!
						ExecutableElement injector = injectorCandidates.get(0);
						matchedInjectors.add(injector);
						toGenerate.put(
							String.format("%sInjector%d", generateRealClassName(cl), iterationNumber),
							new InjectorInfo(injector, tg, targetAnn, this.getProcessorOptions())
						);
						iterationNumber++; //increment is only used by injectors
					} else {
						//matched a finder!
						VariableElement finder = finderCandidates.get(0);
						matchedMethodFinders.add(finder);
						appendMemberFinderDefinition(
							finder,
							tg,
							targetAnn,
							constructorBuilder,
							this.getProcessorOptions()
						);
					}
				}
			}
		}

		//find orphans, throw exception if any are found
		for(ExecutableElement e : injectors)
			if(!matchedInjectors.contains(e))
				throw new OrphanElementException(e);
		for(VariableElement e : methodFinders)
			if(!matchedMethodFinders.contains(e))
				throw new OrphanElementException(e);

		//iterate over the map and generate the classes
		for(String injName : toGenerate.keySet()) {
			MethodContainer target = toGenerate.get(injName).target;
			TypeSpec injectorClass = TypeSpec.classBuilder(injName)
				.addModifiers(Modifier.PUBLIC)
				.superclass(cl.asType())
				.addSuperinterface(ClassName.get(IInjector.class))
				.addMethod(constructorBuilder.build())
				.addMethod(buildStringReturnMethod("name", injName))
				.addMethod(buildStringReturnMethod("reason", toGenerate.get(injName).reason))
				.addMethod(buildStringReturnMethod("targetClass", this.getProcessorOptions().obfuscateInjectorMetadata
					? targetClass.data.nameMapped.replace('/', '.')
					: targetClass.data.name.replace('/', '.')))
				.addMethod(buildStringReturnMethod("methodName", this.getProcessorOptions().obfuscateInjectorMetadata
					? target.data.nameMapped : target.data.signature.name))
				.addMethod(buildStringReturnMethod("methodDesc", this.getProcessorOptions().obfuscateInjectorMetadata
					? target.descriptorObf : target.data.signature.name))
				.addMethods(generateDummies(cl))
				.addMethod(generateInjector(toGenerate.get(injName), this.processingEnv))
				.build();

			this.injectors.add(writeClass(
				this.processingEnv.getFiler(),
				this.options.outputPackage != null
					? this.options.outputPackage
					: packageName,
				injName,
				injectorClass
			));
		}
	}

	/**
	 * Generates a fake no-op Mixin to ensure that all the classes that need transformations
	 * are registered to require them in Mixin environments.
	 * @param fqn the fully-qualified name of the class
	 * @since 0.8.2
	 */
	public void generateFakeMixinClass(String fqn) {
		int lastPeriod = fqn.lastIndexOf('.');
		String pkg = fqn.substring(0, Math.max(0, lastPeriod));
		String clazz = fqn.substring(lastPeriod + 1);

		AnnotationSpec.Builder mixinAnn = AnnotationSpec.builder(ClassName.get(
			"org.spongepowered.asm.mixin",
			"Mixin"
		));

		for(ClassName targetName : this.targets) {
			mixinAnn.addMember("value", "$T.class", targetName);
		}

		TypeSpec spec = TypeSpec.classBuilder(clazz)
			.addModifiers(Modifier.PUBLIC)
			.addAnnotation(mixinAnn.build())
			.build();

		writeClass(this.processingEnv.getFiler(), pkg, clazz, spec);
	}

	/**
	 * Generates the Service Provider file for the generated injectors.
	 */
	private void generateServiceProvider() {
		try {
			FileObject serviceProvider = this.processingEnv.getFiler().createResource(
				StandardLocation.CLASS_OUTPUT,
				"",
				"META-INF/services/ftbsc.lll.IInjector"
			);
			PrintWriter out = new PrintWriter(serviceProvider.openWriter());
			this.injectors.forEach(out::println);
			this.injectors.clear();
			out.close();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
}
