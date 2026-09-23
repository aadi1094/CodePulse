# Implementation state
Current phase: 0
Current slice: Phase 0 complete (2026-09-23). Phase 1 not started; waiting for the developer to begin it.
Last working commit: 1c141fd feat(engine): add equality, defensive-copy, and debugger practice for Phase 0 (Slice C).

## Implemented and personally verified
- Slice A (2026-09-23): spec pack moved to `docs/spec/`, links fixed, git initialized on `main`, ignore policy, README. Verified by Claude; developer verification pending.
- Slice B (2026-09-23), implemented and tests pass, developer verification pending:
  - `backend/pom.xml`: Java 21 via `maven.compiler.release`, JUnit Jupiter 6.0.3 (test scope), compiler plugin 3.15.0, Surefire 3.5.6. Versions match those managed by Spring Boot 4.1.1 (checked on Maven Central).
  - Maven Wrapper 3.3.4 generated with `mvn wrapper:wrapper` (only-script mode: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, no jar).
  - `dev.codepulse.engine.FileMetrics` record (`relativePath`, `packageName`, `physicalLoc`, `methodCount`) with compact-constructor validation.
  - `dev.codepulse.engine.PackageSummary`: defensive `List.copyOf`, duplicate-path rejection, `groupByPackage()` → `TreeMap<String, TreeSet<String>>`, `render()` sorted text with plain loops.
  - `dev.codepulse.Phase0Main`: runnable sample (to be deleted in Phase 4).
  - `PackageSummaryTest`: sorted grouping, empty input, duplicate path rejection.
- Slice C (2026-09-23), implemented and tests pass, developer verification pending:
  - `Phase0Main --duplicate` deliberately constructs a duplicate path so `PackageSummary` throws `IllegalArgumentException`; uncaught, prints a 4-frame stack trace.
  - Traced in `jdb`: breakpoint in `rejectDuplicatePaths`, stepped to the throw, inspected `seen` and `m.relativePath()`.
  - `CollectionEqualityTest`: records deduplicate in a HashSet; a class without equals is identity-only; mutating a key after insertion strands it (`contains`/`remove` false, `size` 1).
  - `PackageSummaryTest.isNotAffectedByLaterChangesToTheInputList`: defensive copy verified.
  - `docs/learning/phase-0-java-bridge.notes`: concept notes, jdb transcript, VS Code debugger steps.

## Commands and observed results
- 2026-09-23 `java -version` → OpenJDK 21.0.11 (Homebrew). `mvn -version` → Apache Maven 3.9.16. Toolchain ready for Phase 0.
- 2026-09-23 `git init -b main` → empty repository created; no commits.
- 2026-09-23 `cd backend && mvn -q -B wrapper:wrapper` → exit 0, wrapper 3.3.4 files created.
- 2026-09-23 `./mvnw -B test` (first run) → BUILD FAILURE after 2m49s: `Could not transfer artifact org.apache.maven.surefire:surefire-providers:pom:3.5.6 ... Can't assign requested address`. Compilation of 3 main + 1 test class had succeeded. Hypothesis: transient socket/network error, not POM. Evidence: `curl` of the artifact URL returned HTTP 200.
- 2026-09-23 `./mvnw -B test` (retry, no changes) → BUILD SUCCESS. `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` in `PackageSummaryTest`.
- 2026-09-23 `java -cp target/classes dev.codepulse.Phase0Main` → printed 3 package blocks sorted: `(default package)`, `dev.codepulse`, `dev.codepulse.engine`, files sorted within each. Exit 0.
- 2026-09-23 `git commit` ×2 (authorized by developer): eb7de52 chore(repo), 037b466 feat(engine).
- 2026-09-23 `git commit` (authorized): 1c141fd Slice C.
- 2026-09-23 `./mvnw -B test` (Slice C) → BUILD SUCCESS. `CollectionEqualityTest` 3/3, `PackageSummaryTest` 4/4, total `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`.
- 2026-09-23 `java -cp target/classes dev.codepulse.Phase0Main --duplicate` → exit 1, `IllegalArgumentException: duplicate relativePath: src/main/java/App.java` with frames rejectDuplicatePaths:37 ← <init>:29 ← failWithDuplicatePath:42 ← main:16.
- 2026-09-23 `jdb -classpath target/classes -sourcepath src/main/java dev.codepulse.Phase0Main --duplicate` with `stop in ...rejectDuplicatePaths` → breakpoint hit line 33; after stepping, `print seen` = `[src/main/java/App.java]`, `print seen.contains(m.relativePath())` = true, then uncaught exception at line 37.

## Known failures or limitations
- No application code exists. Nothing in this repository runs yet.
- Deferred by design until their phase: Spring Boot (4), PostgreSQL/JPA/Flyway (5), sessions/CSRF (6), GitHub acquisition (7), worker (8), React (9), Docker/CI (10).

## Concepts I can explain without assistance
- (developer fills this in; the learning gate is what the developer can explain, not what Claude generated)
- 2026-09-23 confirmed in gate answers: .java → javac → .class bytecode → JVM runs it; editing source has no effect until recompiled; a HashSet key mutated after insertion is searched in the wrong bucket and is lost.
- Candidates from Slice B: JDK vs JVM vs bytecode; Maven lifecycle phases (compile → test-compile → test); what a record generates; `List<FileMetrics>` and `Map<String, Set<String>>`; why `TreeMap`/`TreeSet` give deterministic output; defensive copy with `List.copyOf`; `Set.add` returning false.

## Decisions and deviations from the specification
- 2026-09-23 Spec files were MOVED (not copied) into `docs/spec/` so there is a single source of truth. Reason: root copies would drift. Alternative: keep copies at root (rejected). Evidence: `START-HERE.md`, `Claude-Build-Playbook.md`, and the Blueprint's link to the playbook were updated so relative links still resolve. No specification content was changed.
- 2026-09-23 Phase 0 code will live in `backend/` as a plain Java 21 Maven module (no Spring) under package `dev.codepulse.engine`. Reason: Phases 1–3 build the engine in the same module and Phase 4 adds Spring around it, so nothing is discarded. Alternative: disposable `learning/phase0/` module (rejected: repeats Maven setup in Phase 1).
- 2026-09-23 Phase 0 `FileMetrics` will use a subset of the `java-metrics-v1` field names from Blueprint 9.3 (`relativePath`, `packageName`, `physicalLoc`, `methodCount`) so Phase 2 extends the record instead of renaming it. Phase 0 values are hand-constructed learning data, not parsed source.
- 2026-09-23 ADR files in `docs/decisions/` are written when a decision is implemented (starting Phase 4). Blueprint section 23 already records the core decisions and their rationale.
- 2026-09-23 `docs/architecture/`, `docs/decisions/`, `docs/metrics/`, `docs/learning/` are created when a phase first needs them, not up front.

- 2026-09-23 Learning notes use plain-text `.notes` files in `docs/learning/` (developer preference) instead of Markdown.

## Current learning gate (Phase 0)
- Developer must: run `./mvnw test` themselves; reproduce the breakpoint in VS Code (steps in `docs/learning/phase-0-java-bridge.notes`); explain source → bytecode → execution; explain why mutating a HashSet key is dangerous; answer the Slice B prediction and the understanding questions. Not yet done as of 2026-09-23.
- 2026-09-23 gate answers, first attempt:
  - Source → bytecode → execution: NOT yet understood ("it should get refresh"). Re-taught with a 5-line Hello.java run by hand (javac → Hello.class → java → javap -c). Developer to repeat it and answer again.
  - Mutated HashSet key: mostly correct (new hashCode → wrong locker → lost). Corrected: hashCode comes from the object's content, not from being a new object, and the object stays in its old locker.
  - Failures vs Errors: mixed up. Corrected: Failure = assertion got a wrong answer; Error = test crashed with an unexpected exception.
  - 2026-09-23 second attempt at Q1: "Java converts human-readable text into machine instructions through compilation and execution." Right direction, but does not name javac, bytecode/.class, or the JVM, and says "machine instructions" instead of bytecode. Hello.java prediction (run `java Hello` without recompiling) not yet answered.
  - 2026-09-23 third attempt: filled blanks correctly (.java → javac/compiler → .class → bytecode → JVM). Predicted `java Hello` after editing without recompiling prints the OLD text "Hi from Java" (correct: the JVM runs the existing .class; only javac updates it).
  - Gate status: MET for the concept questions on 2026-09-23. Failures vs Errors was corrected in teaching; revisit briefly in Phase 1 when the first test fails.

## Next smallest slice
- Developer completes the Phase 0 gate checklist at the end of `docs/learning/phase-0-java-bridge.notes`. No tag yet (`v0.1.0` is after Phase 3).
- Phase 1, slice 1: `SourceInventory` over a trusted temporary fixture directory using `Path`/`Files`, returning a deterministic sorted list of normalized relative paths. Concept lesson first: NIO paths, try-with-resources, checked exceptions.

## Suggested commit
- None pending.

