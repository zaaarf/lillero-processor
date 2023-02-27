# Lillero-processor
Lillero-processor is an annotation processor made to simplify development of [Lillero](https://git.fantabos.co/lillero) patches, minimising the amount of boilerplate code needed.

## How to use
First things first, add the processor and its dependencies to your `build.gradle`:
```groovy
dependencies {
	implementation 'ftbsc.lll:processor:0.1.0'
	annotationProcessor 'com.squareup:javapoet:1.13.0'
	annotationProcessor 'ftbsc:lll:0.2.1'
	annotationProcessor 'ftbsc.lll:processor:0.1.0'
}
```

That's about all the effort you need to put in! Now, this:
```java
package example.patches;
import net.minecraft.client.Minecraft;
import ftbsc.lll.processor.annotations.*;
@Patch(value = Minecraft.class, reason = "crash the game as soon as it loads")
public class SamplePatch implements Opcodes {
	@Target
	public void tick() {};
	@Injector
	public static void yourCustomInjector(ClassNode clazz, MethodNode main) {
		InsnList insnList = new InsnList();
		insnList.add(new InsnNode(POP));
		main.instructions.insert(insnList);
	}
}
```

will automatically generate:

```java
package example.patches;
import ftbsc.lll.IInjector;
public class SamplePatchInjector implements IInjector {
	public String name()        { return "SamplePatch"; }
	public String reason()      { return "crash the game as soon as it loads"; }
	public String targetClass() { return "net.minecraft.client.Minecraft"; }
	public String methodName()  { return "func_71407_l"; } // tick()
	public String methodDesc()  { return "()V"; } // void, no args 
	public void inject(ClassNode clazz, MethodNode main) {
		SamplePatch.yourCustomInjector(clazz, main);
	}
}
```

Happy patching!