# Implementation state
Current phase: 1
Current slice: 1B — physical line counts for .java files with a 256 KiB bounded read. Implemented, 30 tests pass, uncommitted; developer verification pending.
Last working commit: 2403fcf feat(engine): add source inventory with exclusions and sorted relative paths (Slice 1A, pushed to origin/main).

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

- Phase 1 Slice 1A (2026-09-23), implemented and tests pass, developer verification pending:
  - `engine/SourceFile` record (relativePath POSIX, sizeBytes), `engine/InventoryResult` record (files defensive-copied, excludedFileCount).
  - `engine/SourceInventory.scan(Path)`: `Files.walk` in try-with-resources, NOFOLLOW_LINKS, exact directory-segment exclusions, `UncheckedIOException` unwrapped, sorted by path.
  - `dev.codepulse.InventoryMain`: developer-only CLI harness, not an HTTP entry point.
  - `SourceInventoryTest` (8 tests, @TempDir fixtures): empty, nested + sorted, byte sizes incl. CRLF, exclusions counted, exact-name matching, symlinks not followed, missing root, file as root.
  - `docs/learning/phase-1-source-inventory.notes`.

- Phase 1 Slice 1B (2026-09-23), implemented and tests pass, developer verification pending:
  - `engine/PhysicalLineCounter`: byte-level physicalLoc (empty 0, trailing break adds nothing, \r\n / \n / lone \r = one break), 8 KiB buffer, CR state carried across buffers, stops once more than maxBytes are read.
  - `engine/FileSizeLimitExceededException` (checked, extends IOException) with relativePath and limitBytes.
  - `SourceFile.physicalLines` (Integer, null = not measured for non-.java files). `SourceInventory` takes `maxJavaFileBytes` (default 256 KiB), checks Files.size first, then the counter enforces it on bytes read.
  - Tests: `PhysicalLineCounterTest` 11 hand-counted cases (incl. CRLF split at buffer boundary, UTF-8 multibyte, limit); `SourceInventoryTest` +4 (Java-only counting, at-limit accepted, over-limit stops scan, large non-Java not limited).

## Commands and observed results
- 2026-09-23 `java -version` → OpenJDK 21.0.11 (Homebrew). `mvn -version` → Apache Maven 3.9.16. Toolchain ready for Phase 0.
- 2026-09-23 `git init -b main` → empty repository created; no commits.
- 2026-09-23 `cd backend && mvn -q -B wrapper:wrapper` → exit 0, wrapper 3.3.4 files created.
- 2026-09-23 `./mvnw -B test` (first run) → BUILD FAILURE after 2m49s: `Could not transfer artifact org.apache.maven.surefire:surefire-providers:pom:3.5.6 ... Can't assign requested address`. Compilation of 3 main + 1 test class had succeeded. Hypothesis: transient socket/network error, not POM. Evidence: `curl` of the artifact URL returned HTTP 200.
- 2026-09-23 `./mvnw -B test` (retry, no changes) → BUILD SUCCESS. `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` in `PackageSummaryTest`.
- 2026-09-23 `java -cp target/classes dev.codepulse.Phase0Main` → printed 3 package blocks sorted: `(default package)`, `dev.codepulse`, `dev.codepulse.engine`, files sorted within each. Exit 0.
- 2026-09-23 `git commit` ×2 (authorized by developer): eb7de52 chore(repo), 037b466 feat(engine).
- 2026-09-23 `git commit` (authorized): 1c141fd Slice C.
- 2026-09-23 `git remote add origin https://github.com/aadi1094/CodePulse.git && git push -u origin main` → pushed 4 commits to the empty public repo; `main` tracks `origin/main` at 620cca9. Secret scan of tracked files before push found nothing sensitive.
- 2026-09-23 `./mvnw -B test` (Slice C) → BUILD SUCCESS. `CollectionEqualityTest` 3/3, `PackageSummaryTest` 4/4, total `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`.
- 2026-09-23 `java -cp target/classes dev.codepulse.Phase0Main --duplicate` → exit 1, `IllegalArgumentException: duplicate relativePath: src/main/java/App.java` with frames rejectDuplicatePaths:37 ← <init>:29 ← failWithDuplicatePath:42 ← main:16.
- 2026-09-23 `jdb -classpath target/classes -sourcepath src/main/java dev.codepulse.Phase0Main --duplicate` with `stop in ...rejectDuplicatePaths` → breakpoint hit line 33; after stepping, `print seen` = `[src/main/java/App.java]`, `print seen.contains(m.relativePath())` = true, then uncaught exception at line 37.

- 2026-09-23 `./mvnw -B test` (Slice 1A) → BUILD SUCCESS, `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0` (SourceInventoryTest 8/8).
- 2026-09-23 `java -cp target/classes dev.codepulse.InventoryMain .` (in backend/) → 14 included files listed sorted, `excluded files: 22` (target/ contents).

- 2026-09-23 `./mvnw -B test` (Slice 1B, first run) → BUILD FAILURE, 2 failures: `expected: <22> but was: <21>` (UTF-8 text length) and `sizeBytes=13 ... but was 14` (App.java). Hypothesis: hand-counted expectations wrong, code right. Evidence: Python count gave 14 bytes and 21 chars / 26 bytes. Fixed expectations (also the unreached 27→26).
- 2026-09-23 `./mvnw -B test` (Slice 1B, after fix) → BUILD SUCCESS, `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`.
- 2026-09-23 `InventoryMain .` line counts match `wc -l` for SourceInventory.java (145), PhysicalLineCounter.java (72), Phase0Main.java (44).

## Known failures or limitations
- Slice 1A: symlinks are skipped without being counted; the spec's workspace rules (Phase 7) reject them at extraction. MAIN/TEST/OTHER classification and the workspace interface are not implemented yet (Slice 1C). UTF-8 validity is not checked in inventory (deferred to parsing, Phase 2). The 256 KiB cap applies only to .java files; the Blueprint's archive-level caps arrive with acquisition in Phase 7.
- Slice 1A walks into excluded directories and counts their files instead of skipping the subtree; fine for bounded workspaces, revisit if scans get slow.
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

- 2026-09-23 Default excluded directory names: `.git, node_modules, vendor, target, build, generated, generated-sources`. Blueprint 9.2 says "generated-source directories" without naming them; `generated` and `generated-sources` are my concrete choice. Matching is exact and case-sensitive on directory segments only.
- 2026-09-23 physicalLoc counted on raw bytes (not decoded chars) and only for `.java` files; non-Java files get `null` (not measured), never 0. Lone `\r` counts as a line break, following JLS 3.4 line terminators (Blueprint 9.3 only states CRLF normalization).
- 2026-09-23 Size-limit violation throws checked `FileSizeLimitExceededException` and fails the whole scan (Blueprint 11.3 "reject run"), not a per-file skip.
- 2026-09-23 Inventory uses `Files.walk` + try-with-resources (teaches resource closing, the Phase 1 gate). Alternative `Files.walkFileTree` with SKIP_SUBTREE avoids descending into excluded folders; deferred.

## Current learning gate (Phase 1)
- Developer must explain: what inventory counts vs what Java analysis does; why Files.walk is closed (try-with-resources, OS handles, even on exception); how paths are normalized and exclusions applied; when to use a checked exception.

- 2026-09-23 first attempt at Slice 1A questions:
  - Inventory vs analysis: half right (inventory lists folders/files); missing that analysis reads and parses file contents.
  - Why handles are returned on exception: NOT understood ("OS gets limited time"). Re-taught with a Door AutoCloseable demo (open → work → throw → close runs → catch).
  - Why relative paths: mixed up with "Path does not touch the disk". Re-taught: same result on every computer, no private laptop path leaked.
  - Prediction for the 4-file folder: not answered yet.
  - Second attempt: Q1 correct (lists vs opens). Q2 named try-with-resources; corrected that it calls close(). Q3 and the 4-file prediction left unanswered; developer said "I got the topic" and asked to continue with 1B. Recorded as the developer's decision; revisit Q3 at the Phase 1 checkpoint.
  - Gate status: partially met; proceeding at developer's request.

## Current learning gate (Phase 0, passed)
- Developer must: run `./mvnw test` themselves; reproduce the breakpoint in VS Code (steps in `docs/learning/phase-0-java-bridge.notes`); explain source → bytecode → execution; explain why mutating a HashSet key is dangerous; answer the Slice B prediction and the understanding questions. Not yet done as of 2026-09-23.
- 2026-09-23 gate answers, first attempt:
  - Source → bytecode → execution: NOT yet understood ("it should get refresh"). Re-taught with a 5-line Hello.java run by hand (javac → Hello.class → java → javap -c). Developer to repeat it and answer again.
  - Mutated HashSet key: mostly correct (new hashCode → wrong locker → lost). Corrected: hashCode comes from the object's content, not from being a new object, and the object stays in its old locker.
  - Failures vs Errors: mixed up. Corrected: Failure = assertion got a wrong answer; Error = test crashed with an unexpected exception.
  - 2026-09-23 second attempt at Q1: "Java converts human-readable text into machine instructions through compilation and execution." Right direction, but does not name javac, bytecode/.class, or the JVM, and says "machine instructions" instead of bytecode. Hello.java prediction (run `java Hello` without recompiling) not yet answered.
  - 2026-09-23 third attempt: filled blanks correctly (.java → javac/compiler → .class → bytecode → JVM). Predicted `java Hello` after editing without recompiling prints the OLD text "Hi from Java" (correct: the JVM runs the existing .class; only javac updates it).
  - Gate status: MET for the concept questions on 2026-09-23. Failures vs Errors was corrected in teaching; revisit briefly in Phase 1 when the first test fails.

## Next smallest slice
- Slice 1C: classify each file (MAIN for src/main/**, TEST for src/test/**, OTHER_SOURCE otherwise; Java candidates vs other files) as an enum, add counts, and introduce the `SourceWorkspace` interface so Phase 7 can supply a downloaded workspace without changing inventory.

## Suggested commit
- `feat(engine): count physical lines with bounded reads and size limit`

