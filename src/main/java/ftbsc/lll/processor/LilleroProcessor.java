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
	 * of the generated injector files.
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
	public boolean isValidInjector(TypeElement elem) {
		TypeMirror classNodeType = this.processingEnv.getElementUtils().getTypeElement("org.objectweb.asm.tree.ClassNode").asType();
		TypeMirror methodNodeType = this.processingEnv.getElementUtils().getTypeElement("org.objectweb.asm.tree.MethodNode").asType();
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
		Map<ExecutableElement, InjectorInfo> toGenerate = new HashMap<>();
		Map<VariableElement, FinderInfo> matchedFinders = new HashMap<>();

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

					toGenerate.put(info.injector, info);
				} else if(matched instanceof VariableElement) { // matched a finder!
					FinderInfo info = new FinderInfo(cl, (VariableElement) matched, tg, targetAnn);
					matchedFinders.put(info.proxy, info);
				}
			}
		}

		// take care of TypeProxies and FieldProxies
		for(VariableElement proxyVar : finders) {
			ProxyType type = getProxyType(proxyVar);
			if(type == ProxyType.TYPE) {
				matchedFinders.put(proxyVar, new FinderInfo(cl, proxyVar, null, null));
			} else if(type == ProxyType.FIELD) {
				matchedFinders.put(proxyVar, new FinderInfo(cl, proxyVar, null, null));
			}
		}

		// find orphans, throw exception if any are found
		for(ExecutableElement e : injectors)
			if(!toGenerate.containsKey(e))
				throw ErrorReporter.orphan(e);
		for(VariableElement e : finders)
			if(!matchedFinders.containsKey(e))
				throw ErrorReporter.orphan(e);

		// register parameter finders or generate constructor initializers
		for(FinderInfo info : matchedFinders.values()) {
			if(info.proxy.getEnclosingElement() instanceof ExecutableElement) {
				InjectorInfo injInfo = toGenerate.get((ExecutableElement) info.proxy.getEnclosingElement());
				if(injInfo != null) {
					injInfo.finderParams.add(info);
				} else {
					throw ErrorReporter.orphan(info.proxy);
				}
			} else {
				info.appendToMethodSpec(constructorBuilder, false, this.options);
			}
		}

		// iterate over the map and generate the classes
		for(InjectorInfo injInfo : toGenerate.values()) {
			MethodContainer target = injInfo.target;
			TypeSpec injectorClass = TypeSpec.classBuilder(injInfo.name)
				.addModifiers(Modifier.PUBLIC)
				.superclass(cl.asType())
				.addSuperinterface(ClassName.get(IInjector.class))
				.addMethod(constructorBuilder.build())
				.addMethod(buildStringReturnMethod("name", injInfo.name))
				.addMethod(buildStringReturnMethod("reason", injInfo.reason))
				.addMethod(buildStringReturnMethod("targetClass", this.getProcessorOptions().obfuscateInjectorMetadata
					? targetClass.data.nameMapped.replace('/', '.')
					: targetClass.data.name.replace('/', '.')))
				.addMethod(buildStringReturnMethod("methodName", this.getProcessorOptions().obfuscateInjectorMetadata
					? target.data.nameMapped : target.data.signature.name))
				.addMethod(buildStringReturnMethod("methodDesc", this.getProcessorOptions().obfuscateInjectorMetadata
					? target.descriptorObf : target.data.signature.name))
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

		// generate real mixin
		AnnotationSpec.Builder mixinAnn = AnnotationSpec.builder(ClassName.get(
			"org.spongepowered.asm.mixin",
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

		TypeSpec.Builder spec = TypeSpec.classBuilder(clazz).addModifiers(Modifier.PUBLIC);
		if(isPseudo) {
			spec.addAnnotation(AnnotationSpec.builder(ClassName.get("org.spongepowered.asm.mixin", "Pseudo")).build());
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
