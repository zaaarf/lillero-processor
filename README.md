# Lillero-processor
Lillero-processor is an annotation processor made to simplify development of [Lillero](https://github.com/zaaarf/lillero) patches, minimising the
amount of boilerplate code needed.

Please note that to work its magic, the processor needs the code of the target of your patches available in the compile
time environment. If your build system does not allow that, you may want to use the library manually instead.

## Usage
First things first, add the processor to your build system. The example shows Gradle, but any build system supporting
Maven repositories should work:
```groovy
dependencies {
	implementation 'ftbsc.lll:processor:<whatever the latest version is>'
	annotationProcessor 'ftbsc.lll:processor:<whatever the latest version is>'
}
```
Once it's done, you will be able to use Lillero without insane amounts of boilerplate. The processor works by generating
new classes overriding the patches you write. The examples are abstract because stubs look better, but it should work
even with concrete classes - in fact, modifiers are generally disregarded.

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

The annotation `@Patch` specifies which class should be patched. `@Target` must be used on a stub with the same
descriptor (return type and parameter types) and name as the target method. Its parameter `of` specifies who is
referring to it. It follows that multiple patches may be made to different methods within a same class, as long as the
injector name is always specified correctly. The `@Target` annotation is repeatable, and may therefore be used to have
multiple injections on the same method.

If for any reason you don't want to check the full signature, but rather attempt a lookup by name only, simply add
`strict = false` to your `@Target`. This is not recommended, as you may not always have the guarantee that you are the
only one tampering with runtime code.

You may find yourself not wanting to use the actual name of the target method in the stub. Maybe you have a name
conflict, or maybe you are just trying to patch a constructor (`<init>`) or a static constructor (`<clinit>`), whose
names you cannot type. Simply add `methodName = "name"` to your `@Target` annotation, and the specified name will
overrule the stub's name.

Note that you can omit the `ClassNode` parameter in your injector method if you don't use it (which is most cases).

### Finders
While patching, you may find yourself needing to refer to other methods and fields, both within your code and within the
target. This can be simplified considerably through the `@Find` annotation. The behaviour of `@Find` differs
considerably depending on what kind of element it is looking for. Let's see the three cases.

```java
@Find(SomeClass.class)
FieldProxy fieldName;
```

This is the simplest case. This finder will match any field named `fieldName` within the class `SomeClass`.

```java
@Find(SomeClass.class)
TypeProxy typeProxy;
```

A `TypeProxy` is used to represent a class type. The `name` parameter, if given, will be ignored, and so will be the
actual field name. The resulting `TypeProxy` will be a representation of `SomeClass`.

```java
@Find(SomeClass.class)
MethodProxy methodProxy;

@Target(of = "methodProxy")
abstract void someMethod(int i, int j);
```

MethodProxies need a stub to correctly match their target. Matching by name is also supported - either by setting the
`strict` flag of the `@Target` annotation or by setting the `name` parameter in the `@Find` annotation - but is not
recommended. The class specified within `@Find`, much like with fields, will be considered the parent class of the
method you are looking for.

Whenever the class is unspecified in a finder (except in TypeProxy's case, which is an error) it will be assumed to be
the class containing the `@Find` annotation - that is, the patch class.

Lillero provides three classes to use these in your injectors: `FieldProxyInsnNode`, `MethodProxyInsnNode` and
`TypeProxyInsnNode`. Each wraps the equivalent [ObjectWeb ASM](https://asm.ow2.io/) `InsnNode`. For instance:

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
You may find yourself needing to interact with a private inner class - which you can't reference explicitly by
`Name.class`. The processor has your back, once again. Every annotation which tries to match a class (i.e. `@Patch` and
`@Find`) also provides a `inner` parameter. This allows you to specify the "unaccessible part" of the name, to be
appended with a `$` in front of what is extracted from the Class object. In the unfortunate case of multiple nesting
with private classes, just place any extra `$` yourself (i.e. `SampleClass$PrivateInnerFirst$InnerSecond` should be
reached with a `@Patch(value = SampleClass.class, inner = {"PrivateInnerFirst", "InnerSecond"}`).

#### Anonymous classes
Anonymous classes are trickier, because [they are apparently unavailable](https://stackoverflow.com/questions/75849759/how-to-find-an-anonymous-class-in-the-annotation-processing-environment) in the normal annotation processing
environment. That means that, unlike with other classes, the processor cannot make sure that they exist, and it cannot
easily extract information about their fields and methods.

Anonymous classes are numbered by the compiler in the order it meets them, starting from 1. The following rules apply to
patching an anonymous class with the processor:
* Use the compiler-assigned number as `inner` parameter, next to the parent class.
* Write any method stub normally.
* Finders for anonymous class fields may be made, but their type has to be specified explicitly, unlike all others, by 
  using the `type()` and `typeInner()` parameters.
	- Local variables of the containing method may sometimes be accessible by an anonymous class. Make sure to use the
      `name` parameter of the finder appending the `val$` prefix, such as `val$actualName`.

Most if not all of this (although I have not tested it) should apply to local classes as well.

### Hybrid setups
Sometimes, you may want to manually write IInjectors yourself in a project which also uses the processor. In these
cases, you don't want to create the service provider (the `META-INF/services/ftbsc.lll.IInjector` file) yourself, to 
avoid conflicts. Simply add the annotation `@BareInjector` on top of the IInjector class(es) you wrote.

### Obfuscation support
You may pass a mappings file to the processor by adding this to your `build.gradle`:

```groovy
compileJava { //mappings for lillero-processor
	options.compilerArgs << '-AmappingsFile=remote_url_or_local_path'
}
```
This feature is powered by [Lillero-mapper](https://github.com/zaaarf/lillero-mapper). It supports multiple formats;
my personal recommendation is `tinyv2`, but see the project's README for more information.

#### Limitations
Many mapping formats like to "trim" their contents by not repeating information about overriding methods. Generally,
Lillero will *attempt* to find its way up to the top-level method, every time, to compensate for that.

However, the inherent limitations of the AST environment are such that the processor *will* fail to find its way up
the tree if type erasure is involved. The actual overriding methods will be synthetic, but the processor can't know
anything about them since it operates before the compiler creates them. To mitigate this, you can use the annotation
`@Overridden`, which allows the user to write a stub for the top-level parent (thus specifying the signature of the
method which will actually carry the obfuscation information).

```java
@Overridden(parent = IGenericInterface.class)
abstract<T extends SomeClass> void someMethod(T input);

@Target(of = "someInjector")
abstract<T extends SubClassOfSomeClass> void someMethod(T input);
```

`@Overidden` will know what `@Target` stuff it's aimed at by the name; if that proves not enough for your case, a `by`
field allows you to customize that (obviously, combined with `@Target`'s `methodName`, which it will **ignore**). It
has an obligatory `parent` field, and a number of optional ones that you should already be familiar with.

The base will be unaffected by `@Overridden` for all purposes except obfuscation of the name.

### Mixin support
If you want to use [Lillero-mixin](https://github.com/zaaarf/lillero-mixin) in your project, the processor can generate
the fake `@Mixin` class for you. Just pass it the `fakeMixin` in form of a fully-qualified name for the generated class
and it will do so!

This feature can support `@BareInjector`s *if* you also add a `@Patch` annotation specifying what they are targeting.
The extra annotation will be effectively ignored for all purposes except the fake Mixin generation.

### Other processor arguments
In the same way you pass mappings, you may pass `false` or `0` to the boolean arguments `badPracticeWarnings` and
`anonymousClassWarning`, to disable, respectively, warnings about bad practices in usage, and reminders of the unsafety
of anonymous classes.

## Conclusions and Extras
The processor's API should remain mostly stable, unless glaring issues are found, at least until version `1.0.0`.
It has changed much in the past versions, but I am confident that the current design is capable of handling most,
if not all, problems. More features may be added to deal with new cases that come up, but existing features should
remain stable and largely unchanged (from the outside, at least).

Though most of the original code is gone, you can still read my dev diary about developing its first version
[here](https://zaaarf.foo/blog/to-kill-a-boilerplate/) if you are curious about the initial ideas behind it.

In conclusion, let me just say: happy patching!
