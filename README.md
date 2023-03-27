# Lillero-processor
Lillero-processor is an annotation processor made to simplify development of [Lillero](https://git.fantabos.co/lillero) patches, minimising the amount of boilerplate code needed.

An important note: to make things as easy as it is, the processor assumes that you have the code of the target available in your development environment. As of 0.5.0, it will not work otherwise.

## Usage
First things first, add the processor to your `build.gradle`:
```groovy
dependencies {
	implementation 'ftbsc.lll:processor:<whatever the latest version is>'
	annotationProcessor 'ftbsc.lll:processor:<whatever the latest version is>'
}
```
Once it's done, you will be able to use Lillero without insane amounts of boilerplate. The processor works by generating new classes overriding the patches you write. The examples are abstract because stubs look better, but it should work even with concrete classes - in fact, modifiers are generally disregarded.

The examples are about Minecraft, but you can use this with any Lillero-based project.

### A basic patch
Let's say we want to simply the example provided in Lillero's README:
```java
public class SamplePatch implements IInjector {
	public String name()		{ return "SamplePatch"; }
	public String reason()      { return "crash the game as soon as it loads"; }
	public String targetClass() { return "net.minecraft.client.Minecraft"; }
	public String methodName()	{ return "func_71407_l"; } //Searge name for tick()
	public String methodDesc()	{ return "()V"; } //void, no args
	public void inject(ClassNode clazz, MethodNode main) {
		InsnList insnList = new InsnList();
		insnList.add(new InsnNode(POP));
		main.instructions.insert(insnList);
	}
}
```

The simplified version looks like this:

```java
@Patch(Minecraft.class)
public abstract class SamplePatch {
	@Target(of = "injectorName")
	abstract void tick();

	@Injector(reason = "crash the game as soon as it loads")
	public void injectorName(ClassNode clazz, MethodNode main) {
		InsnList insnList = new InsnList();
		insnList.add(new InsnNode(POP));
		main.instructions.insert(insnList);		
	}
}
```

The annotation `@Patch` specifies which class should be patched. `@Target` must be used on a stub with the same descriptor (return type and parameter types) and name as the target method. Its parameter `of` specifies who is referring to it. It follows that multiple patches may be made to different methods within a same class, as long as the injector name is always specified correctly. The `@Target` annotation is repeatable, and may therefore be used to have multiple injections on the same method.

If for any reason you don't want to check the full signature, but rather attempt a lookup by name only, simply add `strict = false` to your `@Target`. This is not recommended, as you may not always have the guarantee that you are the only one tampering with runtime code.

You may find yourself not wanting to use the actual name of the target method in the stub. Maybe you have a name conflict, or maybe you are just trying to patch a constructor (`<init>`) or a static constructor (`<clinit>`), whose names you cannot type. Simply add `methodName = "name"` to your `@Target` annotation, and the specified name will overrule the stub's name.

### Finders
While patching, you may find yourself needing to refer to other methods and fields, both within your code and within the target. This can be simplified considerably through the `@Find` annotation. The behaviour of `@Find` differs considerably depending on what kind of element it is looking for. Let's see the three cases.

```java
@Find(SomeClass.class)
FieldProxy fieldName;
```

This is the simplest case. This finder will match any field named `fieldName` within the class `SomeClass`. If the class is unspecified, the one contained within `@Patch` is used instead. You may overrule the field name by adding `name = "actualName"` to your `@Find` annotation.

```java
@Find(SomeClass.class)
TypeProxy typeProxy;
```

A `TypeProxy` is used to represent a class type. The `name` parameter, if given, will be ignored, and so will be the actual field name. The resulting `TypeProxy` will be a representation of `SomeClass`.

```java
@Find(SomeClass.class)
MethodProxy methodProxy;

@Target(of = "methodProxy")
abstract void someMethod(int i, int j);
```

MethodProxies need a stub to correctly match their target. Matching by name is also supported - either by setting the `strict` flag of the `@Target` annotation or by setting the `name` parameter in the `@Find` annotation - but is not recommended. The class specified within `@Find`, much like with fields, will be considered the parent class of the method you are looking for. If omitted, it defaults to the one specified in @Patch.

Lillero provides three classes to use these in your injectors: `FieldProxyInsnNode`, `MethodProxyInsnNode` and `TypeProxyInsnNode`. Each wraps the equivalent [ObjectWeb ASM](https://asm.ow2.io/) `InsnNode`. For instance:

```java
@Find(SomeClass.class)
TypeProxy typeProxy;

// target(s) and other code)

@Injector
public void inject(ClassNode clazz, MethodNode main) {
	main.instructions.insert(new FieldProxyInsnNode(GETSTATIC, typeProxy));
}
```

Obviously, it's up to you to use the correct opcode. The processor can't read your mind (yet).

### Private Inner Classes
You may find yourself needing to interact with a private inner class - which you can't reference explicitly by `Name.class`. The processor has your back, once again. Every annotation which tries to match a class (i.e. `@Patch` and `@Find`) also provides a `className` parameter. `className` will behave differently depending on whether the `value` (the Class object you are passing it) is set. If it is not set, `className` will act as the fully-qualified name of your target (i.e. your.package.SampleClass$Inner). Otherwise, it will act as the "unaccassible part" of the name, to be appended with a `$` on front to what is extracted from the Class object.

#### Anonymous classes
Anonymous classes are trickier, because [they are apparently unavailable](https://stackoverflow.com/questions/75849759/how-to-find-an-anonymous-class-in-the-annotation-processing-environment) in the normal annotation processing environment. That means that, unlike with other classes, the processor cannot make sure that they exist, and it cannot easily extract information about their fields and methods.

Anonymous classes are numbered by the compiler in the order it meets them, starting from 1. The following rules apply to patching an anonymous class with the processor, as of version 0.5.0:
* Use the compiler-assigned number as `className` parameter, next to the parent class.
* Write any method stub normally.
* Finders for anonymous class fields may be made, but their type has to be specified explicitly, unlike all others, by using the `type()` and `typeInner()` parameters.
	- Local variables of the containing method may sometimes be accessible by an anonymous class. Make sure to use the `name` parameter of the finder appending the `val$` prefix, such as `val$actualName`.

The extra `@Find` parameters (`type()' and `typeInner()`) are meant to be temporary, hacky workarounds until a better way is found. Expect them to change and be removed without notice. 

Most if not all of this (although I have not tested it) should apply to local classes as well.

### Obfuscation
You may pass a mappings file to the processor by adding this to your `build.gradle`:

```groovy
compileJava { //mappings for lillero-processor
	options.compilerArgs << '-AmappingsFile=remote_url_or_local_path'
}
```

This feature is in a very early stage, and currently only works with TSRG files (Minecraft MCP to SRG mappings). More formats will be added in the future - possibly as a separate library the processor will rely on.

### Other processor arguments
In the same way you pass mappings, you may pass `false` or `0` to the boolean arguments `badPracticeWarnings` and `anonymousClassWarning`, to disable, respectively, warnings about bad practices in usage, and reminders of the unsafety of anonymous classes.

## Conclusions and Extras
Since reaching version 0.5.0, the processor will hopefully be mostly stable. It has changed much in the past versions, but I am confident that we now found a solution capable of handling most, if not all, cases. 

Though most of that code is gone, you can still read my dev diary about developing its first version [here](https://fantabos.co/posts/zaaarf/to-kill-a-boilerplate/) if you are curious about the initial ideas behind it.

In conclusion, let me just say: happy patching!
