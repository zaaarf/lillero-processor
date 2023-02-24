package ftbsc.lll.processor;

import com.squareup.javapoet.*;
import ftbsc.lll.IInjector;
import ftbsc.lll.processor.annotations.Injector;
import ftbsc.lll.processor.annotations.Patch;
import ftbsc.lll.tools.DescriptorBuilder;
import ftbsc.lll.tools.SrgMapper;
import org.gradle.internal.impldep.org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("ftbsc.lll.processor.annotations.Patch")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class LilleroProcessor extends AbstractProcessor {

	private SrgMapper mapper;

	@Override
	public void init(ProcessingEnvironment processingEnv) {
		try {
			mapper = new SrgMapper(Files.lines(Paths.get("build/createMcpToSrg/output.tsrg")));
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
		super.init(processingEnv);
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		Set<TypeElement> validInjectors = new HashSet<>();
		for (TypeElement annotation : annotations) {
			if(isValidInjector(annotation))
				validInjectors.add(annotation);
			else processingEnv.getMessager().printMessage(
				Diagnostic.Kind.WARNING,
				"Missing valid inject() method on @Injector class " + annotation.getQualifiedName() + "."
			);
		}

		if(validInjectors.isEmpty())
			return false;

		validInjectors.forEach(this::generateInjector);
		generateServiceProvider(validInjectors);
		return true;
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
				&& e.getModifiers().contains(Modifier.PUBLIC)
				&& e.getModifiers().contains(Modifier.STATIC)
				&& params.size() == 2
				&& processingEnv.getTypeUtils().isSameType(params.get(0), classNodeType)
				&& processingEnv.getTypeUtils().isSameType(params.get(1), methodNodeType);
		});
	}

	private MethodSpec findAnnotatedMethod(TypeElement cl, Class<? extends Annotation> ann) {
		return MethodSpec.overriding(
			(ExecutableElement) cl.getEnclosedElements()
				.stream()
				.filter(e -> e.getAnnotation(ann) != null)
				.findFirst()
				.get() //will never be null so can ignore warning
		).build();
	}

	private String getClassOrString(TypeName n) { //jank but fuck you
		return n.isPrimitive() ? n + ".class" : "\"" + n + "\"";
	}

	private String generateDescriptorBuilderString(MethodSpec m) {
		StringBuilder sb = new StringBuilder();
		sb.append("new $T().setReturnType(");
		sb.append(getClassOrString(m.returnType)).append(")");
		m.parameters.forEach(p -> sb.append(".addParameter(").append(getClassOrString(p.type)).append(")"));
		sb.append(".build();");
		return sb.toString();
	}

	private String getSrgMethodName(MethodSpec m) {
		return m.name; //TODO;
	}

	private void generateInjector(TypeElement cl) {
		Patch ann = cl.getAnnotation(Patch.class);
		MethodSpec targetMethod = findAnnotatedMethod(cl, Target.class);
		MethodSpec injectorMethod = findAnnotatedMethod(cl, Injector.class);

		String packageName = cl.getQualifiedName().toString().replace("." + cl.getSimpleName().toString(), "");

		String className = cl.getQualifiedName().toString();
		String simpleClassName = cl.getSimpleName().toString();

		String injectorClassName = className + "Injector";
		String injectorSimpleClassName = simpleClassName + "Injector";

		MethodSpec name = MethodSpec.methodBuilder("name")
			.addModifiers(Modifier.PUBLIC)
			.returns(String.class)
			.addStatement("return $S", simpleClassName)
			.build();

		MethodSpec reason = MethodSpec.methodBuilder("reason")
			.addModifiers(Modifier.PUBLIC)
			.returns(String.class)
			.addStatement("return $S", ann.reason())
			.build();

		MethodSpec targetClass = MethodSpec.methodBuilder("targetClass")
			.addModifiers(Modifier.PUBLIC)
			.returns(String.class)
			.addStatement("return $S", mapper.getMcpClass(Type.getInternalName(ann.value())))
			.build();

		MethodSpec methodName = MethodSpec.methodBuilder("methodName")
			.addModifiers(Modifier.PUBLIC)
			.returns(String.class)
			.addStatement("return $S", getSrgMethodName(targetMethod))
			.build();

		MethodSpec methodDesc = MethodSpec.methodBuilder("methodDesc")
			.addModifiers(Modifier.PUBLIC)
			.returns(String.class)
			.addCode(generateDescriptorBuilderString(targetMethod), DescriptorBuilder.class)
			.build();

		MethodSpec inject = MethodSpec.methodBuilder("inject")
			.addModifiers(Modifier.PUBLIC)
			.returns(void.class)
			.addParameter(ParameterSpec.builder(ClassNode.class, "clazz").build())
			.addParameter(ParameterSpec.builder(MethodNode.class, "main").build())
			.addStatement("$S.$S(clazz, main)", className, injectorMethod.name)
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
		} catch(IOException e) {}
	}

	private void generateServiceProvider(Set<TypeElement> inj) {
		try {
			FileObject serviceProvider = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "", "ftbsc.lll.IInjector");
			PrintWriter out = new PrintWriter(serviceProvider.openWriter());
			inj.forEach(i -> out.println(i.getQualifiedName() + "Injector"));
			out.close();
		} catch(IOException e) {}
	}
}