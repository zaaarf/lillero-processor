package ftbsc.lll.processor.utils;

import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;

/**
 * Collection of static utils that rely on JavaPoet to function.
 */
public class JavaPoetUtils {
	/**
	 * Builds a {@link MethodSpec} for a public method whose body simply returns a {@link String}.
	 * @param name the name of the method
	 * @param returnString the {@link String} to return
	 * @return the built {@link MethodSpec}
	 */
	public static MethodSpec buildStringReturnMethod(String name, String returnString) {
		return MethodSpec.methodBuilder(name)
			.addModifiers(Modifier.PUBLIC)
			.addAnnotation(Override.class)
			.returns(String.class)
			.addStatement("return $S", returnString)
			.build();
	}

	/**
	 * Generates a {@link HashSet} of dummy overrides for every abstract method in a given class,
	 * represented as a {@link TypeElement}.
 	 * @param clazz the given class
	 * @return a {@link HashSet} containing the generated {@link MethodSpec}s
	 * @since 0.5.0
	 */
	public static HashSet<MethodSpec> generateDummies(TypeElement clazz) {
		HashSet<MethodSpec> specs = new HashSet<>();
		clazz
			.getEnclosedElements()
			.stream()
			.filter(e -> e instanceof ExecutableElement)
			.map(e -> (ExecutableElement) e)
			.forEach(e -> {
				if(e.getModifiers().contains(Modifier.ABSTRACT))
					specs.add(MethodSpec.overriding(e)
						.addStatement("throw new $T($S)", RuntimeException.class, "This is a stub and should not have been called")
						.build()
					);
			});
		return specs;
	}

	/**
	 * Generates the "real" class name for a {@link TypeElement}. The "real" class name
	 * is identical to the simple name for normal classes, and is the result of recursively
	 * joining the simple name with the parent's with "$" for inner classes.
	 * @param cl the class to generate it for
	 * @return the real class name
	 * @since 0.8.1
	 */
	public static String generateRealClassName(TypeElement cl) {
		StringBuilder name = new StringBuilder(cl.getSimpleName().toString());
		while(cl.getEnclosingElement() instanceof TypeElement) {
			cl = (TypeElement) cl.getEnclosingElement();
			name.insert(0, '$');
			name.insert(0, cl.getSimpleName());
		}
		return name.toString();
	}

	/**
	 * Writes a Java source file from a JavaPoet spec.
	 * @param filer the processing environment's {@link Filer}
	 * @param pkg the package to output this to
	 * @param name the simple name of the class
	 * @param spec the {@link TypeSpec} for it
	 * @return the fully qualified name of the written class
	 * @since 0.8.2
	 */
	public static String writeClass(Filer filer, String pkg, String name, TypeSpec spec) {
		String fqn = String.format("%s.%s", pkg, name);
		JavaFile javaFile = JavaFile.builder(pkg, spec).build();
		try(PrintWriter out = new PrintWriter(filer.createSourceFile(fqn).openWriter())) {
			javaFile.writeTo(out);
		} catch(IOException e) {
			throw new RuntimeException(e);
		}

		return fqn;
	}
}
