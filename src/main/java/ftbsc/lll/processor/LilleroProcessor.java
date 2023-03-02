package ftbsc.lll.processor;

import com.squareup.javapoet.*;
import ftbsc.lll.IInjector;
import ftbsc.lll.processor.annotations.*;
import ftbsc.lll.proxies.FieldProxy;
import ftbsc.lll.proxies.MethodProxy;
import ftbsc.lll.tools.SrgMapper;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ftbsc.lll.processor.ASTUtils.*;

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
					validInjectors.forEach(this::generateInjector);
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
	 * Generates the Injector corresponding to the given class.
	 * Basically implements the {@link IInjector} interface for you.
	 * @param cl the {@link TypeElement} for the given class
	 */
	private void generateInjector(TypeElement cl) {
		SrgMapper mapper;
		try { //TODO: cant we get it from local?
			URL url = new URL("https://data.fantabos.co/output.tsrg");
			InputStream is = url.openStream();
			mapper = new SrgMapper(new BufferedReader(new InputStreamReader(is,
				StandardCharsets.UTF_8)).lines());
			is.close();
		} catch(IOException e) {
			throw new RuntimeException("Could not open the specified TSRG file!", e);
		}

		Patch ann = cl.getAnnotation(Patch.class);
		String targetClassCanonicalName;
		try {
			targetClassCanonicalName = ann.value().getCanonicalName();
		} catch(MirroredTypeException e) {
			targetClassCanonicalName = e.getTypeMirror().toString();
		} //pretty sure class names de facto never change but better safe than sorry
		String targetClassSrgName = mapper.getMcpClass(targetClassCanonicalName.replace('.', '/'));

		ExecutableElement targetMethod = findAnnotatedMethods(cl, Target.class).get(0); //there should only be one
		String targetMethodDescriptor = descriptorFromMethodSpec(targetMethod);
		String targetMethodSrgName = mapper.getSrgMember(
			targetClassCanonicalName.replace('.', '/'),
			targetMethod.getSimpleName() + " " + targetMethodDescriptor
		);

		ExecutableElement injectorMethod = findAnnotatedMethods(cl, Injector.class).get(0); //there should only be one

		Element packageElement = cl.getEnclosingElement();
		while (packageElement.getKind() != ElementKind.PACKAGE)
			packageElement = packageElement.getEnclosingElement();

		String packageName = packageElement.toString();
		String injectorSimpleClassName = cl.getSimpleName().toString() + "Injector";
		String injectorClassName = packageName + "." + injectorSimpleClassName;

		MethodSpec stubOverride = MethodSpec.overriding(targetMethod)
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
			.addStatement("super." + injectorMethod.getSimpleName() + "(clazz, main)", TypeName.get(cl.asType()))
			.build();

		TypeSpec injectorClass = TypeSpec.classBuilder(injectorSimpleClassName)
			.addModifiers(Modifier.PUBLIC)
			.superclass(cl.asType())
			.addSuperinterface(ClassName.get(IInjector.class))
			.addMethod(buildStringReturnMethod("name", cl.getSimpleName().toString()))
			.addMethod(buildStringReturnMethod("reason", ann.reason()))
			.addMethod(buildStringReturnMethod("targetClass", targetClassSrgName.replace('/', '.')))
			.addMethod(buildStringReturnMethod("methodName", targetMethodSrgName))
			.addMethod(buildStringReturnMethod("methodDesc", targetMethodDescriptor))
			.addMethods(generateRequestedProxies(cl, mapper))
			.addMethod(stubOverride)
			.addMethod(inject)
			.build();

		JavaFile javaFile = JavaFile.builder(packageName, injectorClass).build();

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
				FindMethod ann = m.getAnnotation(FindMethod.class);
				String targetMethodName = ann.name().equals("") ? m.getSimpleName().toString() : ann.name();
				try {
					MethodSpec.Builder b = MethodSpec.overriding(m);
					Method targetMethod = ann.parent().getMethod(
						targetMethodName,
						ann.params()
					);
					b.addStatement("$T bd = $T.builder($S)",
						MethodProxy.Builder.class,
						MethodProxy.class,
						targetMethodName
					);
					b.addStatement("bd.setParent($S)", targetMethod.getDeclaringClass().getCanonicalName());
					b.addStatement("bd.setModifier($L)", targetMethod.getModifiers());
					for(Class<?> p : targetMethod.getParameterTypes())
						b.addStatement("bd.addParameter($T.class)", p);
					b.addStatement("bd.setReturnType($T.class)", targetMethod.getReturnType());
					b.addStatement("return bd.build()");
					generated.add(b.build());
				} catch(NoSuchMethodException e) {
					processingEnv.getMessager().printMessage(
						Diagnostic.Kind.ERROR,
						"Method not found: " + targetMethodName
					);
				}
			});
		findAnnotatedMethods(cl, FindField.class)
			.stream()
			.filter(m -> !m.getModifiers().contains(Modifier.STATIC))
			.filter(m -> !m.getModifiers().contains(Modifier.FINAL))
			.forEach(m -> {
				FindField ann = m.getAnnotation(FindField.class);
				String targetFieldName = ann.name().equals("") ? m.getSimpleName().toString() : ann.name();
				try {
					MethodSpec.Builder b = MethodSpec.overriding(m);
					Field targetField = ann.parent().getField(targetFieldName);
					b.addStatement("$T bd = $T.builder($S)",
						FieldProxy.Builder.class,
						FieldProxy.class,
						targetFieldName
					);
					b.addStatement("bd.setParent($S)", targetField.getDeclaringClass().getCanonicalName());
					b.addStatement("bd.setModifier($L)", targetField.getModifiers());
					b.addStatement("bd.setType($T.class)", targetField.getType());
					b.addStatement("return bd.build()");
					generated.add(b.build());
				} catch(NoSuchFieldException e) {
					processingEnv.getMessager().printMessage(
						Diagnostic.Kind.ERROR,
						"Field not found: " + targetFieldName + " in class " + ann.parent().getCanonicalName()
					);
				}
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
}