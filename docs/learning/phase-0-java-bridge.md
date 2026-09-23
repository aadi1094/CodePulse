# Phase 0 learning notes — Java bridge

Written 2026-09-23 while building the Phase 0 Maven project. These are the concepts the learning
gate asks me to explain without help. Rewrite any entry in your own words once you can.

## Source → bytecode → execution

1. `javac` (part of the JDK) compiles `.java` text into `.class` files containing **bytecode**.
2. Bytecode is a portable instruction set for the **JVM**, not machine code for the CPU.
3. `java -cp target/classes dev.codepulse.Phase0Main` starts a JVM, which loads the class by its
   fully qualified name from the classpath, verifies it, and executes `main`.
4. The JIT compiler inside the JVM turns hot bytecode into machine code at runtime.

Observed: Maven wrote `target/classes/dev/codepulse/engine/FileMetrics.class`; running from that
directory needed no source files.

## Maven lifecycle

`./mvnw test` ran these phases in order: `validate → compile → test-compile → test`. Plugins do the
work: `maven-compiler-plugin` compiles, `maven-surefire-plugin` runs tests. Asking for a later phase
runs all earlier ones. `package` would come next and build the jar. The wrapper script `mvnw`
downloads the Maven version pinned in `.mvn/wrapper/maven-wrapper.properties`, so every machine
builds with the same Maven.

Failure read on 2026-09-23: `Could not transfer artifact ... Can't assign requested address`. That
is an OS socket error during download, not a POM error. Evidence: `curl` of the artifact URL gave
HTTP 200, and an unchanged retry passed.

## Records

`record FileMetrics(String relativePath, String packageName, int physicalLoc, int methodCount)`
generates: a canonical constructor, accessors named after the components, and `equals`/`hashCode`/
`toString` over all components. Fields are final. The compact constructor (no parameter list) runs
validation before assignment. A record is shallowly immutable: a `List` component would still be
mutable unless copied.

## Generics

`List<FileMetrics>`: a list that only holds FileMetrics, checked at compile time.
`Map<String, Set<String>>`: keys are strings, each value is a set of strings. Read inside-out.

## Equality and hash-based collections (the gate question)

`HashSet`/`HashMap` place an element in a bucket chosen by `hashCode()`, then use `equals()` inside
that bucket. Three cases, all demonstrated in `CollectionEqualityTest`:

| Case | Result |
|---|---|
| Record with same values | `equals` true, one element in the set |
| Class without `equals`/`hashCode` | Identity only; two equal-looking objects both stored |
| Key mutated after insertion | `hashCode` now points at a different bucket; `contains` and `remove` return false while `size()` is still 1. The element is stranded. |

Why it matters here: Phase 3 keeps import-graph edges in sets. Edge types must be immutable records
so equality can never drift after insertion.

## Defensive copy

`PackageSummary` stores `List.copyOf(metrics)`. Test `isNotAffectedByLaterChangesToTheInputList`
mutates and clears the caller's list after construction and the summary is unchanged. Without the
copy, the object's state would belong partly to whoever holds the original list.

## Deterministic output

`TreeMap`/`TreeSet` iterate in sorted order; `HashMap` order is unspecified and may change between
runs or JVM versions. Same input must give the same report, so the engine sorts.

## Debugger

### Command line (`jdb`, ships with the JDK)

```
cd backend
./mvnw -q compile
jdb -classpath target/classes -sourcepath src/main/java dev.codepulse.Phase0Main --duplicate
> stop in dev.codepulse.engine.PackageSummary.rejectDuplicatePaths
> run
> where        # call stack: rejectDuplicatePaths ← <init> ← failWithDuplicatePath ← main
> locals       # metrics = List12, seen not yet assigned
> next         # step one line; repeat
> print seen   # "[src/main/java/App.java]" on the second loop iteration
> print seen.contains(m.relativePath())   # true → the throw is about to happen
> cont
> quit
```

Session recorded 2026-09-23: breakpoint hit at line 33, stepped to line 37, `seen` held one path,
`m.relativePath()` equalled it, exception thrown and reported as uncaught.

### VS Code (Extension Pack for Java is installed)

1. Open `backend/src/main/java/dev/codepulse/engine/PackageSummary.java`.
2. Click in the gutter left of the line `if (!seen.add(m.relativePath())) {` to set a red breakpoint.
3. Open `Phase0Main.java`. Above `main` click **Debug** (CodeLens). To pass `--duplicate`, use
   Run → Add Configuration → Java, and set `"args": "--duplicate"` in the generated launch config.
4. When the breakpoint hits, look at **Variables**: expand `seen` and `m`. Press **F10** (Step Over)
   twice per loop iteration and watch `seen` grow. On the second iteration the `if` is true.
5. Press **F11** (Step Into) on the `throw` line to see the exception object being built.
6. The **Call Stack** panel shows the same four frames as the stack trace, top = current.

### Reading a stack trace

```
Exception in thread "main" java.lang.IllegalArgumentException: duplicate relativePath: src/main/java/App.java
	at dev.codepulse.engine.PackageSummary.rejectDuplicatePaths(PackageSummary.java:37)   <- thrown here
	at dev.codepulse.engine.PackageSummary.<init>(PackageSummary.java:29)                 <- constructor
	at dev.codepulse.Phase0Main.failWithDuplicatePath(Phase0Main.java:42)
	at dev.codepulse.Phase0Main.main(Phase0Main.java:16)                                  <- program start
```

Top line: exception type and message. Frames below: newest first. `<init>` is the JVM's name for a
constructor. Go to the first frame in *your* code with the line number.
