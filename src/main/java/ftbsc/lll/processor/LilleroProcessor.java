package ftbsc.lll.processor;

import com.squareup.javapoet.*;
import ftbsc.lll.IInjector;
import ftbsc.lll.processor.annotations.*;
import ftbsc.lll.processor.containers.ClassContainer;
import ftbsc.lll.processor.containers.FinderInfo;
import ftbsc.lll.processor.containers.InjectorInfo;
import ftbsc.lll.processor.containers.MethodContainer;
import ftbsc.lll.processor.reporting.ErrorReporter;
import ftbsc.lll.processor.utils.ASTUtils;
import ftbsc.lll.proxies.ProxyType;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
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
	 * of the IInjector files.
	 */
	public final Set<String> injectors = new HashSet<>();

	/**
	 * A {@link Map} of {@link ClassName}s representing the classes that
	 * are being targeted; the value is a boolean that determines whether
	 * this is to be added as a class or as a string.
	 */
	public final Map<ClassName, Boolean> targets = new HashMap<>();

	@Override
	public Set<String> getSupportedOptions() {
		return ProcessorOptions.SUPPORTED;
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latest();
	}

	private ProcessorOptions options = null;

	/**
	 * Returns the {@link ProcessorOptions} for this instance, creating the object if
	 * it hasn't been already.
	 * @return the {@link ProcessorOptions} for this instance
	 */
	public ProcessorOptions getProcessorOptions() {
		if(this.options == null) this.options = new ProcessorOptions(this.processingEnv);
		return this.options;
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		ProcessorOptions options = this.getProcessorOptions();
		for(TypeElement annotation : annotations) {
			if(annotation.getQualifiedName().contentEquals(Patch.class.getName())) {
				for(Element e : roundEnv.getElementsAnnotatedWith(annotation)) {
					try {
						TypeElement type = (TypeElement) e;
						if(options.fakeMixin != null && e.getAnnotation(BareInjector.class) != null) {
							this.markClassAsTarget(type);
						} else if(this.isValidInjector(type)) {
							this.generateClasses(type);
							if(options.fakeMixin != null) {
								this.markClassAsTarget(type);
							}
						}
					} catch(RuntimeException ex) {
						ErrorReporter.handleRuntimeException(this.processingEnv, ex, e);
					}
				}
			} else if(annotation.getQualifiedName().contentEquals(BareInjector.class.getName())) {
				TypeMirror injectorType = this.processingEnv.getElementUtils().getTypeElement(this.options.apiPackage + ".IInjector").asType();
				for(Element e : roundEnv.getElementsAnnotatedWith(annotation)) {
					TypeElement type = (TypeElement) e;
					if(this.processingEnv.getTypeUtils().isAssignable(e.asType(), injectorType)) {
						this.injectors.add(type.getQualifiedName().toString());
					} else {
						this.processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, String.format(
							"Class %s annotated with @BareInjector is not an instance of IInjector, skipping...",
							type.getQualifiedName()
						));
					}
				}
			}
		}

		this.generateFakeMixinClass();
		this.generateServiceProvider();

		return !this.injectors.isEmpty();
	}

	/**
	 * This checks whether a given class contains the requirements to be parsed into a Lillero injector.
	 * It must have at least one method annotated with {@link Target}, and one method annotated with {@link Injector}
	 * that must take in either a ClassNode and MethodNode (from ObjectWeb's ASM library) or only a MethodNode.
	 * @param elem the element to check.
	 * @return whether it can be converted into a valid {@link IInjector}.
	 */
	public boolean isValidInjector(TypeElement elem) {
		TypeMirror classNodeType = this.processingEnv.getElementUtils().getTypeElement(this.options.asmPackage + ".ClassNode").asType();
		TypeMirror methodNodeType = this.processingEnv.getElementUtils().getTypeElement(this.options.asmPackage + ".MethodNode").asType();
		if(
			elem.getEnclosedElements()
				.stream()
				.anyMatch(e -> e.getAnnotation(Target.class) != null)
			&&
				elem.getEnclosedElements()
					.stream()
					.filter(e -> e instanceof ExecutableElement)
					.anyMatch(e -> {
						// must have @Injector
						if(e.getAnnotation(Injector.class) == null) {
							return false;
						}

						// can't also have @Target
						if(e.getAnnotation(Target.class) != null) {
							return false;
						}

						// get parameters (finders aside)
						List<VariableElement> effectiveParams = ((ExecutableElement) e).getParameters()
							.stream()
							.filter(p -> p.getAnnotation(Find.class) == null)
							.collect(Collectors.toList());

						// effective params must be either MethodNode or ClassNode and MethodNode (in any order)
						return (
							effectiveParams.size() == 1
								&& this.processingEnv.getTypeUtils().isSameType(effectiveParams.get(0).asType(), methodNodeType)
						) || (
							effectiveParams.size() == 2
								&& effectiveParams.stream().anyMatch(p -> this.processingEnv.getTypeUtils().isSameType(p.asType(), classNodeType))
								&& effectiveParams.stream().anyMatch(p -> this.processingEnv.getTypeUtils().isSameType(p.asType(), methodNodeType))
						);
				})
		) {
			return true;
		}

		// print warning
		this.processingEnv.getMessager().printMessage(
			Diagnostic.Kind.WARNING,
			String.format("Missing valid @Injector method in @Patch class %s, skipping...", elem)
		);

		return false;

	}

	/**
	 * Marks the given class as a "target" for purposes of generating the fake mixin.
	 * @param type the class in question
	 */
	public void markClassAsTarget(TypeElement type) {
		Patch ann = type.getAnnotation(Patch.class);

		boolean asClass = ann.fqn().isEmpty();

		ClassName name;
		if(asClass) {
			name = ClassName.get((TypeElement) this.processingEnv.getTypeUtils().asElement(
				getTypeFromAnnotation(ann, Patch::value, this.processingEnv)
			));
		} else {
			String fqn = ann.fqn();
			int lastDot = fqn.lastIndexOf('.');
			String[] classNames = fqn.substring(lastDot + 1).split("\\$");
			name = ClassName.get(
				fqn.substring(0, lastDot),
				classNames[0],
				Arrays.copyOfRange(classNames, 1, classNames.length)
			);
		}

		for(String inner : ann.inner()) {
			name = name.nestedClass(inner);
			if(!ASTUtils.shouldValidate(inner)) {
				asClass = false;
			}
		}

		if(asClass) {
			this.targets.put(name, true);
		} else {
			// verifiable classes are always preferred, so if one class appears
			// multiple times "true" should always win
			this.targets.putIfAbsent(name, false);
		}
	}

	/**
	 * Generates the injector(s) contained in the given class.
	 * Basically implements the {@link IInjector} interface for you.
	 * @param cl the {@link TypeElement} for the given class
	 */
	public void generateClasses(TypeElement cl) {
		// find class information
		Patch patchAnn = cl.getAnnotation(Patch.class);
		ProcessorOptions opts = this.getProcessorOptions();
		ClassContainer targetClass = ClassContainer.from(
			patchAnn,
			Patch::value,
			patchAnn.fqn(),
			patchAnn.inner(),
			opts
		);

		// find annotated elements
		List<ExecutableElement> targets = findAnnotatedEnclosedElements(cl, Target.class);
		List<ExecutableElement> injectors = findAnnotatedEnclosedElements(cl, Injector.class);
		List<VariableElement> finders = findAnnotatedEnclosedElements(cl, Find.class);

		// find annotated parameters
		for(ExecutableElement injector : injectors) {
			for(VariableElement p : injector.getParameters()) {
				if(p.getAnnotation(Find.class) != null) {
					finders.add(p);
				}
			}
		}

		// initialize the constructor builder
		MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder();
		constructorBuilder.addModifiers(Modifier.PUBLIC);

		// these are needed to generate the class later (and for validation)
		Map<ExecutableElement, Set<InjectorInfo>> toGenerate = new HashMap<>();
		Map<VariableElement, Set<FinderInfo>> matchedFinders = new HashMap<>();

		int injectorNumber = 0;
		for(ExecutableElement tg : targets) {
			for(Target targetAnn : tg.getAnnotationsByType(Target.class)) {
				Element matched = matchTarget(cl, tg, targetAnn, injectors, finders, this.processingEnv);
				if(matched instanceof ExecutableElement) { // matched an injector!
					injectorNumber++; // increment is only used by injectors
					InjectorInfo info = new InjectorInfo(
						String.format("%sInjector%d", generateRealClassName(cl), injectorNumber),
						(ExecutableElement) matched,
						tg,
						targetAnn,
						this.getProcessorOptions()
					);

					toGenerate.computeIfAbsent(info.injector, k -> new HashSet<>()).add(info);
				} else if(matched instanceof VariableElement) { // matched a finder!
					FinderInfo info = new FinderInfo(cl, (VariableElement) matched, tg, targetAnn);
					matchedFinders.computeIfAbsent(info.proxy, k -> new HashSet<>()).add(info);
				}
			}
		}

		// take care of TypeProxies and FieldProxies
		for(VariableElement proxyVar : finders) {
			ProxyType type = getProxyType(proxyVar, this.options);
			if(type == ProxyType.TYPE) {
				matchedFinders.computeIfAbsent(proxyVar, k -> new HashSet<>()).add(new FinderInfo(cl, proxyVar, null, null));
			} else if(type == ProxyType.FIELD) {
				matchedFinders.computeIfAbsent(proxyVar, k -> new HashSet<>()).add(new FinderInfo(cl, proxyVar, null, null));
			}
		}

		// find orphans, throw exception if any are found
		for(ExecutableElement e : injectors)
			if(!toGenerate.containsKey(e))
				throw ErrorReporter.orphan(e);
		for(VariableElement e : finders)
			if(!matchedFinders.containsKey(e))
				throw ErrorReporter.orphan(e);

		// list that contains finders that are stored as fields
		// quite ugly, but it's the best i can think of right now
		List<FinderInfo> fieldFinders = new ArrayList<>();

		// register parameter finders or generate constructor initializers
		for(Set<FinderInfo> set : matchedFinders.values()) {
			for(FinderInfo info : set) {
				if(info.proxy.getEnclosingElement() instanceof ExecutableElement) {
					Set<InjectorInfo> injSet = toGenerate.get((ExecutableElement) info.proxy.getEnclosingElement());
					for(InjectorInfo injInfo : injSet) {
						if(injInfo != null) {
							injInfo.validateVisibility(this.options, info.targetStub); // params only need validate stubs
							injInfo.finderParams.add(info);
						} else {
							throw ErrorReporter.orphan(info.proxy);
						}
					}
				} else {
					fieldFinders.add(info);
					info.appendToMethodSpec(constructorBuilder, false, this.options);
				}
			}
		}

		// iterate over the map and generate the classes
		for(Set<InjectorInfo> set : toGenerate.values()) {
			for(InjectorInfo injInfo : set) {
				for(FinderInfo finderInfo : fieldFinders) { // validate visibility of field finders
					injInfo.validateVisibility(this.options, finderInfo.proxy, finderInfo.targetStub);
				}

				MethodContainer target = injInfo.target;
				TypeSpec injectorClass = TypeSpec.classBuilder(injInfo.name)
					.addModifiers(Modifier.PUBLIC)
					.superclass(cl.asType())
					.addSuperinterface(ClassName.get(IInjector.class))
					.addMethod(constructorBuilder.build())
					.addMethod(buildStringReturnMethod("name", injInfo.name))
					.addMethod(buildStringReturnMethod("reason", injInfo.reason))
					.addMethod(buildStringReturnMethod("targetClass", this.getProcessorOptions().obfuscateInjectorMetadata
						? targetClass.nameMapped.replace('/', '.')
						: targetClass.name.replace('/', '.')))
					.addMethod(buildStringReturnMethod("methodName", this.getProcessorOptions().obfuscateInjectorMetadata
						? target.nameMapped : target.name))
					.addMethod(buildStringReturnMethod("methodDesc", this.getProcessorOptions().obfuscateInjectorMetadata
						? target.descriptorMapped : target.descriptor))
					.addMethods(generateDummies(cl))
					.addMethod(injInfo.generateInjector(this.options))
					.build();

				this.injectors.add(writeClass(
					this.processingEnv,
					injInfo.outputPackage,
					injInfo.name,
					injectorClass
				));
			}
		}
	}

	/**
	 * Generates a fake no-op Mixin to ensure that all the classes that need transformations
	 * are registered to require them in Mixin environments.
	 * @since 0.8.2
	 */
	public void generateFakeMixinClass() {
		if(this.options.fakeMixin == null || this.injectors.isEmpty()) {
			return;
		}

		int lastPeriod = options.fakeMixin.lastIndexOf('.');
		String pkg = lastPeriod >= 0
			? options.fakeMixin.substring(0, lastPeriod)
			: "";
		String clazz = options.fakeMixin.substring(lastPeriod + 1);

		// validate mixin-specific requirements
		// these are emitted as warnings because they technically do not impede the processor's functioning,
		// but rather indicate a problem with the framework that will consume these

		// validate that all injectors are in the same package as the mixin plugin
		for(String injFQN : this.injectors) {
			int injLastPeriod = injFQN.lastIndexOf('.');
			String injPkg = injLastPeriod >= 0
				? options.fakeMixin.substring(0, injLastPeriod)
				: "";
			if(!injPkg.equals(pkg)) {
				this.processingEnv.getMessager().printMessage(
					Diagnostic.Kind.WARNING,
					String.format(
						"[Lillero] Generated injector %s was not in the same package as the fake Mixin class (%s), this may cause problems!",
						injFQN,
						options.fakeMixin
					)
				);
			}
		}

		// validate that the mixin package does not currently exist
		PackageElement pkgElem = this.processingEnv.getElementUtils().getPackageElement(pkg);
		if(pkgElem != null && !pkgElem.getEnclosedElements().isEmpty()) {
			this.processingEnv.getMessager().printMessage(
				Diagnostic.Kind.WARNING,
				String.format(
					"[Lillero] Fake Mixin class (%s) was put in an existing package, this may cause problems trying to access other classes within it!",
					options.fakeMixin
				)
			);
		}

		// generate real mixin
		AnnotationSpec.Builder mixinAnn = AnnotationSpec.builder(ClassName.get(
			this.options.mixinPackage,
			"Mixin"
		));

		boolean isPseudo = false;
		for(Map.Entry<ClassName, Boolean> targetName : this.targets.entrySet()) {
			if(targetName.getValue()) {
				mixinAnn.addMember("value", "$T.class", targetName.getKey()); // true = as .class
			} else {
				mixinAnn.addMember("targets", "$S", targetName.getKey().reflectionName()); // false = as string
				isPseudo = true;
			}
		}

		TypeSpec.Builder spec = TypeSpec.interfaceBuilder(clazz).addModifiers(Modifier.PUBLIC);
		if(isPseudo) {
			spec.addAnnotation(AnnotationSpec.builder(ClassName.get(this.options.mixinPackage, "Pseudo")).build());
		}

		writeClass(
			this.processingEnv,
			pkg,
			clazz,
			spec.addAnnotation(mixinAnn.build()).build()
		);
	}

	/**
	 * Generates the Service Provider file for the generated injectors.
	 */
	public void generateServiceProvider() {
		if(this.options.noServiceProvider || this.injectors.isEmpty()) {
			return;
		}

		try {
			FileObject serviceProvider = this.processingEnv.getFiler().createResource(
				StandardLocation.CLASS_OUTPUT,
				"",
				"META-INF/services/" + this.options.apiPackage + ".IInjector"
			);
			PrintWriter out = new PrintWriter(serviceProvider.openWriter());
			this.injectors.forEach(out::println);
			this.injectors.clear();
			out.close();
		} catch(IOException e) {
			this.processingEnv.getMessager().printMessage(
				Diagnostic.Kind.ERROR,
				String.format(
					"[Lillero] An error occurred while generating the service provider file: %s.\n%s",
					e.getMessage(),
					ErrorReporter.stacktraceToString(e)
				)
			);
		}
	}
}
