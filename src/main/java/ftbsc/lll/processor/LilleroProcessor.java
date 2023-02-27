package ftbsc.lll.processor;

import com.squareup.javapoet.*;
import ftbsc.lll.IInjector;
import ftbsc.lll.processor.annotations.Injector;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.processor.annotations.Target;
import ftbsc.lll.processor.exceptions.MappingNotFoundException;
import ftbsc.lll.processor.exceptions.MappingsFileNotFoundException;
import ftbsc.lll.tools.DescriptorBuilder;
import ftbsc.lll.tools.SrgMapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

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
import java.lang.annotation.Annotation;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
				Set<TypeElement> validInjectors = new HashSet<>();
				Set<TypeElement> potentialInjectors =
					roundEnv.getElementsAnnotatedWith(annotation)
						.stream()
						.map(e -> (TypeElement) e)
						.collect(Collectors.toSet());
				for(TypeElement p : potentialInjectors)
					if(isValidInjector(p))
						validInjectors.add(p);
					else processingEnv.getMessager().printMessage(
						Diagnostic.Kind.WARNING,
						"Missing valid @Injector method in @Patch class " + p + ", skipping."
					);
				if(validInjectors.isEmpty())
					return false;
				validInjectors.forEach(this::generateInjector);
				if(this.generatedInjectors.isEmpty())
					return false;
				generateServiceProvider();
				return true;
			}
		}
		return false;
	}

	/**
	 * This checks whether a given class contains the requirements to be parsed into a Lillero injector.
	 * It must have at least one method annotated with {@link Target}, and one method annotated with {@link Injector}
	 * that must be public, static and take in a {@link ClassNode} and a {@link MethodNode}.
	 * @param elem the element to check.
	 * @return whether it can be converted into a valid {@link IInjector}.
	 */
	private boolean isValidInjector(TypeElement elem) {
		TypeMirror classNodeType = processingEnv.getElementUtils().getTypeElement("org.objectweb.asm.tree.ClassNode").asType();
		TypeMirror methodNodeType = processingEnv.getElementUtils().getTypeElement("org.objectweb.asm.tree.MethodNode").asType();
		return elem.getEnclosedElements().stream().anyMatch(e -> e.getAnnotation(Target.class) != null)
			&& elem.getEnclosedElements().stream().anyMatch(e -> {
			List<? extends TypeMirror> params = ((ExecutableType) e.asType()).getParameterTypes();
			return e.getAnnotation(Injector.class) != null
				&& e.getAnnotation(Target.class) == null
				&& e.getModifiers().contains(Modifier.PUBLIC)
				&& e.getModifiers().contains(Modifier.STATIC)
				&& params.size() == 2
				&& processingEnv.getTypeUtils().isSameType(params.get(0), classNodeType)
				&& processingEnv.getTypeUtils().isSameType(params.get(1), methodNodeType);
		});
	}

	/**
	 * Finds, among the methods of a class cl, the one annotated with ann, and tries to build
	 * a {@link MethodSpec} from it.
	 * In case of multiple occurrences, only the first one is returned.
	 * No check existance check is performed within the method.
	 * @param cl the {@link TypeElement} for the class containing the desired method
	 * @param ann the {@link Class} corresponding to the desired annotation
	 * @return the {@link MethodSpec} representing the desired method
	 */
	@SuppressWarnings("OptionalGetWithoutIsPresent")
	private static ExecutableElement findAnnotatedMethod(TypeElement cl, Class<? extends Annotation> ann) {
		return (ExecutableElement) cl.getEnclosedElements()
			.stream()
			.filter(e -> e.getAnnotation(ann) != null)
			.findFirst()
			.get(); //will never be null so can ignore warning
	}

	/**
	 * Builds a type descriptor from the given {@link TypeMirror}
	 * @param t the {@link TypeMirror} representing the desired type
	 * @return a {@link String} containing the relevant descriptor
	 */
	public static String descriptorFromType(TypeMirror t) {
		TypeName type = TypeName.get(t);
		StringBuilder desc = new StringBuilder();
		//add array brackets
		while(type instanceof ArrayTypeName) {
			desc.append("[");
			type = ((ArrayTypeName) type).componentType;
		}
		if(type instanceof ClassName) {
			ClassName var = (ClassName) type;
			desc.append(DescriptorBuilder.nameToDescriptor(var.canonicalName(), 0));
		} else {
			if(TypeName.BOOLEAN.equals(type))
				desc.append("Z");
			else if(TypeName.CHAR.equals(type))
				desc.append("C");
			else if(TypeName.BYTE.equals(type))
				desc.append("B");
			else if(TypeName.SHORT.equals(type))
				desc.append("S");
			else if(TypeName.INT.equals(type))
				desc.append("I");
			else if(TypeName.FLOAT.equals(type))
				desc.append("F");
			else if(TypeName.LONG.equals(type))
				desc.append("J");
			else if(TypeName.DOUBLE.equals(type))
				desc.append("D");
			else if(TypeName.VOID.equals(type))
				desc.append("V");
		}
		return desc.toString();
	}

	/**
	 * Builds a method descriptor from the given {@link ExecutableElement}.
	 * @param m the {@link ExecutableElement} for the method
	 * @return a {@link String} containing the relevant descriptor
	 */
	public static String descriptorFromMethodSpec(ExecutableElement m) {
		StringBuilder methodSignature = new StringBuilder();
		methodSignature.append("(");
		m.getParameters().forEach(p -> methodSignature.append(descriptorFromType(p.asType())));
		methodSignature.append(")");
		methodSignature.append(descriptorFromType(m.getReturnType()));
		return methodSignature.toString();
	}

	/**
	 * Generates the Injector corresponding to the given class.
	 * Basically implements the {@link IInjector} interface for you.
	 * @param cl the {@link TypeElement} for the given class
	 */
	private void generateInjector(TypeElement cl) {
		Patch ann = cl.getAnnotation(Patch.class);
		ExecutableElement targetMethod = findAnnotatedMethod(cl, Target.class);
		ExecutableElement injectorMethod = findAnnotatedMethod(cl, Injector.class);
		SrgMapper mapper;

		try { //TODO: cant we get it from local?
			URL url = new URL("https://data.fantabos.co/output.tsrg");
			InputStream is = url.openStream();
			mapper = new SrgMapper(new BufferedReader(new InputStreamReader(is,
				StandardCharsets.UTF_8)).lines());
			is.close();
		} catch(IOException e) {
			throw new MappingsFileNotFoundException();
		}

		Element packageElement = cl.getEnclosingElement();
		while (packageElement.getKind() != ElementKind.PACKAGE)
			packageElement = packageElement.getEnclosingElement();
		String packageName = packageElement.toString();

		String simpleClassName = cl.getSimpleName().toString();

		String injectorSimpleClassName = simpleClassName + "Injector";
		String injectorClassName = packageName + "." + injectorSimpleClassName;

		MethodSpec name = MethodSpec.methodBuilder("name")
			.addModifiers(Modifier.PUBLIC)
			.returns(String.class)
			.addStatement("return $S", injectorSimpleClassName)
			.build();

		MethodSpec reason = MethodSpec.methodBuilder("reason")
			.addModifiers(Modifier.PUBLIC)
			.returns(String.class)
			.addStatement("return $S", ann.reason())
			.build();

		String targetClassCanonicalName;
		try {
			targetClassCanonicalName = ann.value().getCanonicalName();
		} catch(MirroredTypeException e) {
			targetClassCanonicalName = e.getTypeMirror().toString();
		}

		//pretty sure class names de facto never change but better safe than sorry
		String targetClassSrgName = mapper.getMcpClass(
			targetClassCanonicalName.replace('.', '/')
		);

		if(targetClassSrgName == null)
			throw new MappingNotFoundException(targetClassCanonicalName);

		MethodSpec targetClass = MethodSpec.methodBuilder("targetClass")
			.addModifiers(Modifier.PUBLIC)
			.returns(String.class)
			.addStatement("return $S", targetClassSrgName.replace('/', '.'))
			.build();

		String targetMethodDescriptor = descriptorFromMethodSpec(targetMethod);
		String targetMethodSrgName = mapper.getSrgMember(
			targetClassCanonicalName.replace('.', '/'),
			targetMethod.getSimpleName() + " " + targetMethodDescriptor
		);

		if(targetMethodSrgName == null)
			throw new MappingNotFoundException(targetMethod.getSimpleName() + " " + targetMethodDescriptor);

		MethodSpec methodName = MethodSpec.methodBuilder("methodName")
			.addModifiers(Modifier.PUBLIC)
			.returns(String.class)
			.addStatement("return $S", targetMethodSrgName)
			.build();

		MethodSpec methodDesc = MethodSpec.methodBuilder("methodDesc")
			.addModifiers(Modifier.PUBLIC)
			.returns(String.class)
			.addCode("return $S;", targetMethodDescriptor)
			.build();

		MethodSpec inject = MethodSpec.methodBuilder("inject")
			.addModifiers(Modifier.PUBLIC)
			.returns(void.class)
			.addParameter(ParameterSpec.builder(
				TypeName.get(processingEnv
					.getElementUtils()
					.getTypeElement("org.objectweb.asm.tree.ClassNode").asType()), "clazz").build())
			.addParameter(ParameterSpec.builder(
				TypeName.get(processingEnv
					.getElementUtils()
					.getTypeElement("org.objectweb.asm.tree.MethodNode").asType()), "main").build())
			.addStatement("$T." + injectorMethod.getSimpleName() + "(clazz, main)", TypeName.get(cl.asType()))
			.build();

		TypeSpec injectorClass = TypeSpec.classBuilder(injectorSimpleClassName)
			.addModifiers(Modifier.PUBLIC)
			.addSuperinterface(ClassName.get(IInjector.class))
			.addMethod(name)
			.addMethod(reason)
			.addMethod(targetClass)
			.addMethod(methodName)
			.addMethod(methodDesc)
			.addMethod(inject)
			.build();

		try {
			JavaFileObject injectorFile = processingEnv.getFiler().createSourceFile(injectorClassName);
			PrintWriter out = new PrintWriter(injectorFile.openWriter());
			JavaFile javaFile = JavaFile.builder(packageName, injectorClass).build();
			javaFile.writeTo(out);
			out.close();
			this.generatedInjectors.add(injectorClassName);
		} catch(IOException e) {
			throw new RuntimeException(e);
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
}