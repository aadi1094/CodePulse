# Implementation state
Current phase: 3
Current slice: 3A committed 2026-09-25 (developer authorized commit and push); 3B/3C in progress.
Last working commit: ce0e408 feat(engine): analyze a whole workspace with parse coverage (pushed).

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

- Phase 1 Slice 1C (2026-09-23), implemented and tests pass, developer verification pending:
  - `engine/SourceRole` enum (MAIN, TEST, OTHER_SOURCE) with `classify`: first `src` directory followed by `main`/`test`, anywhere in the path, exact segment match.
  - `engine/SourceWorkspace` interface (`Path root()`) and `engine/LocalDirectoryWorkspace` record. `SourceInventory.scan` now takes a `SourceWorkspace`.
  - `SourceFile` gains `role` (null for non-Java) with invariant: physicalLines and role both set or both null; `SourceFile.other(...)` factory.
  - `InventoryResult` derived counts: `javaFileCount()`, `otherFileCount()`, `javaFileCount(role)`.
  - `InventoryMain` prints role per Java file and a summary.
  - Tests: `SourceInventoryTest` +2 (role classification with multi-module, "mainly", non-Java; first-match rule for nested fixtures).

- Phase 2 Slice 2A (2026-09-23), implemented and tests pass, developer verification pending:
  - Renamed `SourceRole` → `SourceScope` (field `role` → `scope`) to match schema `source_scope` / API `sourceScope`.
  - `pom.xml`: `com.github.javaparser:javaparser-core:3.28.2` (compile scope; core only, no symbol solver).
  - `engine/JavaSourceParser`: own `JavaParser` instance (no StaticJavaParser), language level JAVA_21, UTF-8. Any problem → PARSE_FAILED (recovered partial trees are discarded). Rejects compact/unnamed classes (Java 21 preview, accepted by JavaParser at JAVA_21) as UNSUPPORTED_SYNTAX.
  - `engine/ParseOutcome` (final class; tree only via `Optional<CompilationUnit> syntaxTree()`), `ParseStatus` (PARSED, PARSE_FAILED, SKIPPED), `ParseDiagnostic` (code, nullable line/column; never the parser message), `DiagnosticCode` (SYNTAX_ERROR, UNSUPPORTED_SYNTAX). Max 10 diagnostics kept, total counted.
  - `dev.codepulse.ParseMain`: developer harness, prints status/diagnostics and optional `--tree` (YamlPrinter).
  - Fixtures `src/test/resources/fixtures/parse/`: Java21Features, MissingBrace, UnnamedClass, UnnamedVariable. `JavaSourceParserTest` 7 tests.

- Phase 2 Slice 2B (2026-09-23), implemented and tests pass, developer verification pending:
  - `engine/DeclarationCounts` record (class, interface, enum, record, annotation, anonymous, method, constructor, executable, lambda) with non-negative and executable ≤ method + constructor checks.
  - `engine/DeclarationCounter` extends `VoidVisitorAdapter<Void>`; one walk per file, fresh instance per file, every override calls `super.visit`.
  - Fixtures `fixtures/declarations/Declarations.java` (hand tally in comments) and `ImplicitMembers.java`. `DeclarationCounterTest` 6 tests incl. broken file → no counts.
  - `ParseMain` prints declaration counts for PARSED files.

- Phase 2 Slice 2C (2026-09-23), implemented and tests pass, developer verification pending:
  - `engine/LineMetrics` record (ncloc, commentLines, blankLines).
  - `engine/LineMetricsCalculator.calculate(tree, sourceText)`: ncloc/commentLines = distinct lines covered by code-category tokens (KEYWORD, IDENTIFIER, LITERAL, SEPARATOR, OPERATOR) / COMMENT tokens, using token begin..end lines; blankLines from raw text (space/tab/form feed only), same line-break rules as PhysicalLineCounter.
  - Fixture `fixtures/lines/Mixed.java`; `LineMetricsCalculatorTest` 7 tests with a line-by-line hand count.
  - `ParseMain` prints line metrics.

- Phase 2 Slice 2D (2026-09-23), implemented and tests pass, developer verification pending:
  - `engine/CommentMarkers` record (todoCount, fixmeCount); `engine/CommentMarkerCounter.count(tree)`: COMMENT tokens only, static final `\bTODO\b` / `\bFIXME\b` CASE_INSENSITIVE patterns, occurrences counted with `Matcher.find()`.
  - `CommentMarkerCounterTest` 6 tests (all comment kinds, occurrences, whole-word any case, longer words excluded, markers in strings/identifiers ignored, empty file). `ParseMain` prints markers.

- Phase 2 Slice 2E (2026-09-23), implemented and tests pass, developer verification pending:
  - `engine/DeclarationKind` (METHOD, CONSTRUCTOR, COMPACT_CONSTRUCTOR), `engine/MethodMeasurement` (ownerLabel, signature, declarationKind, hasBody, beginLine, endLine, ncloc) matching schema `method_metric` / API `MethodMetric` minus complexity.
  - `engine/MethodCollector` visitor with a `Deque<String>` owner stack; owner labels: top-level `A`, member `A.B`, local `A#L`, anonymous `A#anonymous@<line>`, enum constant body `A.E#CONST`. Compact constructor signature uses record component types. Sorted by begin line, owner, signature; duplicate (owner, signature, beginLine) → IllegalStateException.
  - `LineMetricsCalculator.isCodeToken` / `codeLinesIn(TokenRange)` shared for method ncloc.
  - `MethodCollectorTest` 6 tests (13 hand-written rows for Declarations.java; count consistency with DeclarationCounter; annotations/javadoc/comments; nested/local owners; reformatting-invariant signature; no methods). `ParseMain` prints methods.

- Phase 2 Slice 2F (2026-09-23), implemented and tests pass, developer verification pending:
  - `engine/ComplexityCalculator` visitor per body: start 1; +1 IfStmt, ForStmt, ForEachStmt, WhileStmt, DoStmt, CatchClause, ConditionalExpr, BinaryExpr AND/OR, non-default SwitchEntry with labels, SwitchEntry guard. Does not enter LambdaExpr, local type declarations, or anonymous class bodies (visits ObjectCreationExpr scope and arguments only). `maxMethodComplexity(list)` → 0 when no bodies.
  - `MethodMeasurement.complexity` (Integer; ≥1 with body, null without — mirrors schema CHECK). `MethodCollector` computes it for bodies.
  - Fixture `fixtures/complexity/Complexity.java`: 20 methods with hand-written "expected N" comments (five-point example, else-if, nested ternary, &&/||, bitwise, grouped switch, old-style switch, guarded pattern case, loops, multi-catch, lambda, anonymous, anonymous with argument, local class, constructor, abstract). `ComplexityCalculatorTest` 4 tests; `MethodCollectorTest` rows updated with complexity.
  - `ParseMain` prints complexity per method and file max.

- Phase 2 Slice 2G (2026-09-23), implemented and tests pass, developer verification pending:
  - `engine/JavaSourceAnalyzer.analyze(SourceWorkspace)`: inventory → per Java file bounded re-read (limit enforced on bytes read, NOFOLLOW_LINKS) → SHA-256 hex → strict UTF-8 (`CodingErrorAction.REPORT`) → parse → measure. Trees dropped per file.
  - `engine/JavaFileAnalysis` record: path, scope, size, physicalLoc, contentSha256, parseStatus, diagnostics, packageName ("" default package), declarations, lines, markers, methods, maxMethodComplexity; invariant: metrics present iff PARSED; failed → null metrics and empty methods.
  - `engine/AnalysisResult`: inventory + javaFiles; eligible/parsed/failed counts; `parseCoverage()` = parsed/eligible, null when 0 eligible.
  - `DiagnosticCode.ENCODING_ERROR`. `dev.codepulse.AnalyzeMain` harness.
  - `JavaSourceAnalyzerTest` 7 tests on a temp workspace (good, broken, invalid-UTF-8, default package, test scope, README, target/ excluded); SHA-256 expectations from `shasum -a 256`.
  - Phase 2 gate fixture: `docs/learning/phase-2-gate-Cart.java` (answers verified with ParseMain, withheld until developer predicts).

- Phase 3 Slice 3A (2026-09-25), implemented and tests pass, developer verification pending:
  - `engine/ImportStatement` record (name without `.*`, isStatic, wildcard, line; `isSingleType()`); `engine/ImportCollector` reads `CompilationUnit.getImports()` and `getTypes()` (top-level fully qualified names only), no visitor.
  - `JavaFileAnalysis` gains `declaredTypeNames` and `imports` (both empty for PARSE_FAILED; invariant extended). `AnalysisResult` gains `importGraph` with invariant: graph nodes == parsed files.
  - `engine/ImportEvidence` record (API `importEvidence` shape): five buckets must add up to `totalImportDeclarations`; `unresolvedExamples` distinct, source order, cap 20, `examplesTruncated`. `NONE` constant for zero imports.
  - `engine/FileEdge` record (schema `dependency_edge`): sourcePath ≠ targetPath, sorted `importedTypes` and ascending distinct `importLines`, both capped at 20 with `truncated`.
  - `engine/ImportGraph` final class, `GRAPH_MODE = "explicit-import-v1"`: sorted edge list, `evidenceByFile` (one entry per parsed file), `ambiguousTypes` (name → declaring files), two `TreeMap<String, TreeSet<String>>` adjacency lists (outgoing/incoming). `observedFanOut/FanIn(path)` → Integer, null for non-parsed files. Rejects edges whose endpoints are not parsed nodes and duplicate edges.
  - `engine/ImportGraphBuilder.build(List<JavaFileAnalysis>)`: pure; pass 1 `HashMap<typeName, TreeSet<path>>` over parsed files (O(types)); pass 2 one lookup per single-type non-static import (O(imports)). Static → static bucket (even `import static X.*`), wildcard → wildcard, missing → unresolved, >1 file → ambiguous (no edge, never first match), exactly one → resolved; self-file → resolved but no edge. Duplicate imports and several types from one target collapse into one edge.
  - `JavaSourceAnalyzer.analyze` calls the builder once after the per-file loop (Blueprint 9.1 "linking"). `AnalyzeMain` prints fanOut/fanIn columns, every edge with evidence, bucket totals, ambiguity warnings, and the limitation note.
  - Fixture `fixtures/imports/ShopImports.java` (line-numbered comments, 7 imports, 5 top-level types + 1 nested). `ImportCollectorTest` 5 tests; `ImportGraphBuilderTest` 9 tests through the real analyzer on a temp workspace (hand-drawn 4-edge shop graph, fan-in/out, evidence buckets incl. self and nested, ambiguity, failed file not a node, examples cap 20/26, one edge for 22 types with truncation, empty workspace, constructor invariants).
  - `docs/learning/phase-3-import-graph.notes`.

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

- 2026-09-23 `git log` shows developer's own commit 0bcf965 (Slice 1B) pushed; `main` in sync with `origin/main`.
- 2026-09-23 `./mvnw -B test` (Slice 1C, first run) → 1 failure in `classifiesJavaFilesByRoleAndCountsThem`: same elements, different order. Hypothesis: expected order wrong ('j' 106 < 'r' 114). Evidence: Python `sorted` agreed. Fixed expected order.
- 2026-09-23 `./mvnw -B test` (Slice 1C, after fix) → BUILD SUCCESS, `Tests run: 32, Failures: 0, Errors: 0, Skipped: 0`.
- 2026-09-23 `InventoryMain .` → `java files: 16 (MAIN 12, TEST 4, OTHER_SOURCE 0)`, `other files: 4`, `excluded files: 30`.

- 2026-09-23 Maven Central metadata: javaparser-core release 3.28.2 (matches spec). JAR exposes LanguageLevel JAVA_21..JAVA_25.
- 2026-09-23 Scratch probe at JAVA_21: valid record/sealed/switch-pattern/text-block → successful; missing brace → unsuccessful but result present (recovered tree); `int _` → rejected ("reserved keyword"); string template → lexical error with no location; unnamed class `void main(){}` → ACCEPTED (name `$COMPACT_CLASS`, `isCompact()` true); empty → successful.
- 2026-09-23 `./mvnw -B test` after SourceRole→SourceScope rename → 32/32 pass.
- 2026-09-23 `./mvnw -B test` (Slice 2A) → BUILD SUCCESS, 39 tests. Review found the diagnostic-cap test was vacuous (input produced 1 problem, verified with ParseMain); replaced with 15 `int _` lines → 15 problems, 10 kept; still 39/39 pass.
- 2026-09-23 `ParseMain` on fixtures: MissingBrace → PARSE_FAILED SYNTAX_ERROR line 8 col 5; UnnamedClass → PARSE_FAILED UNSUPPORTED_SYNTAX line 3 col 1; SourceWorkspace.java `--tree` → PARSED, YAML tree printed.

- 2026-09-23 `git commit` + `git push` (authorized): 449745c Slice 2A.
- 2026-09-23 Slice 2B test-first: stub counter → `Tests run: 45, Failures: 0, Errors: 5` (UnsupportedOperationException), broken-file test already passing. Real visitor → `Tests run: 45, Failures: 0, Errors: 0`, BUILD SUCCESS.
- 2026-09-23 Mutation check: removed `super.visit` in `visit(ClassOrInterfaceDeclaration)` → 3 DeclarationCounterTest failures (e.g. method expected 10 was 0); restored → 45/45 pass.
- 2026-09-23 `ParseMain` on Declarations.java → class 2, interface 1, enum 1, record 1, annotation 1, anonymous 1, method 10, constructor 3, executable 11, lambda 2 (matches hand tally); ImplicitMembers.java → class 1, enum 1, record 1, all members 0; DeclarationCounter.java → method 10, constructor 1, executable 11.

- 2026-09-23 `git commit` + `git push`: fab83f4 Slice 2B.
- 2026-09-23 Token probe (JavaParser 3.28.2): categories KEYWORD/IDENTIFIER/LITERAL/SEPARATOR/OPERATOR/COMMENT/EOL/WHITESPACE_NO_EOL; `"http://x"` is LITERAL; block comment and text block tokens span multiple lines; lone `\r` advances the line number.
- 2026-09-23 `./mvnw -B test` (Slice 2C) → BUILD SUCCESS, `Tests run: 52, Failures: 0, Errors: 0`.
- 2026-09-23 Mutation check: removed CRLF skip in countBlankLines → `windowsAndOldMacLineEndingsGiveTheSameCounts` failed (blank expected 1 was 4); restored → 52/52.
- 2026-09-23 `ParseMain` Mixed.java → ncloc 9, comment 7, blank 4 (hand count); SourceScope.java → ncloc 21, comment 15, blank 2; `grep -cE '^[[:space:]]*$'` also 2; 21+15+2 = `wc -l` 38.

- 2026-09-23 `git commit` + `git push`: 8ae7cf9 Slice 2C.
- 2026-09-23 `./mvnw -B test` (Slice 2D) → BUILD SUCCESS, `Tests run: 58, Failures: 0, Errors: 0`.
- 2026-09-23 Mutation checks: patterns without `\b` → `partOfALongerWordDoesNotCount` failed (todo 3, fixme 1 instead of 0,0); all tokens instead of comments → `markersOutsideCommentsAreIgnored` failed (1,1 instead of 0,0); restored → 58/58.

- 2026-09-23 `git commit` + `git push`: d340153 Slice 2D.
- 2026-09-23 `./mvnw -B test` (Slice 2E, first run) → 1 failure: signature expected `Map<String, Integer>` was `Map<String,Integer>`. Hypothesis: `Type.asString()` renders a normalized type. Evidence: probe showed `Map<String, Integer>`, `Map<String,Integer>`, `Map< String ,  Integer >`, `Map<String,/*c*/Integer>` all → `Map<String,Integer>`; `int []` → `int[]`. Decision: keep normalized form; updated expectation and added reformatting test.
- 2026-09-23 `./mvnw -B test` (Slice 2E) → BUILD SUCCESS, `Tests run: 64, Failures: 0, Errors: 0`.
- 2026-09-23 Mutation check: removed `owners.pop()` in class visitor → Declarations fixture test failed; restored → 64/64.

- 2026-09-23 `git commit` + `git push`: d448faa Slice 2E.
- 2026-09-23 `./mvnw -B test` (Slice 2F) → BUILD SUCCESS, `Tests run: 68, Failures: 0, Errors: 0`; all 20 fixture expectations matched on first run.
- 2026-09-23 Mutation checks: enter lambda bodies → `lambda(boolean)` expected 2 was 4; enter anonymous bodies → `anonymous()` expected 1 was 2; count labels not entries → `grouped(String)` expected 3 was 4; restored → 68/68.

- 2026-09-23 `git commit` + `git push`: 6f0e79e Slice 2F.
- 2026-09-23 `printf 'class A {}\n' | shasum -a 256` → f119fc42…0247; `printf '' | shasum -a 256` → e3b0c442…b855 (used as test expectations).
- 2026-09-23 `./mvnw -B test` (Slice 2G) → BUILD SUCCESS, `Tests run: 75, Failures: 0, Errors: 0`.
- 2026-09-23 Mutation check: UTF-8 decoder `REPLACE` instead of `REPORT` → 3 JavaSourceAnalyzerTest failures (BadBytes became PARSED); restored → 75/75.
- 2026-09-23 `AnalyzeMain .` (backend) → java files 51, parsed 48, failed 3 (MissingBrace SYNTAX_ERROR@8, UnnamedClass UNSUPPORTED_SYNTAX@3, UnnamedVariable SYNTAX_ERROR@6), coverage 94.1%, excluded 81. Max complexity 14 in JavaFileAnalysis and MethodMeasurement constructors. `AnalyzeMain src` → all OTHER_SOURCE (paths lack `src/` prefix; rule working as designed).

- 2026-09-25 `./mvnw -B -q test` (baseline before Phase 3) → exit 0, 75 tests, no failures.
- 2026-09-25 JavaParser 3.28.2 probe: `getImports()` gives name without `.*`, `isStatic()`, `isAsterisk()`, begin line; duplicate imports are kept as separate declarations; `getTypes()` returns top-level types only; `getFullyQualifiedName()` is `pkg.Name`, or `Name` in the default package; empty file → 0 types, 0 imports.
- 2026-09-25 `./mvnw -B test` (Slice 3A, first run) → BUILD SUCCESS, `Tests run: 89, Failures: 0, Errors: 0, Skipped: 0` (ImportCollectorTest 5/5, ImportGraphBuilderTest 9/9); all hand-drawn expectations matched on the first run.
- 2026-09-25 Mutation checks: removed self-edge guard → 3 ImportGraphBuilderTest errors (`IllegalArgumentException: a file cannot depend on itself: src/main/java/app/OrderService.java`); ambiguous → first match → `ambiguousTypeNamesCreateNoEdgeButAWarning` failed (edge `User.java -> a/Dup.java` created); restored → 89/89.
- 2026-09-25 Developer authorized commits and pushes for this session ("commit and push 3A").
- 2026-09-25 `AnalyzeMain .` (backend) → java files 60, parsed 57, failed 3 (fixtures), coverage 95.0%, excluded 95; `observed import edges (explicit-import-v1): 27`; `import declarations: 316  resolved: 27  wildcard: 1  static: 41  ambiguous: 0  unresolved/external: 247`. All 27 edges go from the `dev.codepulse` harnesses to `dev.codepulse.engine` classes; engine-to-engine use is same-package and invisible to the graph (Blueprint 9.6 limitation observed on our own code).

## Known failures or limitations
- Slice 3A: the graph sees explicit single-type non-static imports only. Same-package references, wildcard and static imports, inline fully qualified names, nested-type imports (`a.b.Outer.Inner`), reflection, and DI are not represented; fan-in/fan-out are lower bounds. A type declared in a PARSE_FAILED file is indistinguishable from an external type. Package-level aggregation (Blueprint 9.6 last paragraph, API `PackageSummary`/`PackageGraph`) and impact BFS (9.7) are not built yet. Ambiguity is exposed as `ImportGraph.ambiguousTypes()`; the run-level `{code,message,count}` warning DTO is Phase 8.
- Slice 2G: a UTF-8 byte-order mark is not specially handled. Encoding failures carry no line number. The analyzer does not yet support cancellation or an elapsed-time limit (Blueprint 9.1 CancellationProbe, 11.3 120 s cap); planned with the worker in Phase 8. Import graph and priorities are Phase 3.
- Slice 2F: lambda complexity is not scored (Blueprint 9.4 MVP limitation; lambdas are only counted). Initializer blocks and field initializers are outside the metric. `case null, default ->` is treated as a default entry (adds 0); not separately tested.
- Slice 2A: parser output is not yet connected to inventory (no per-file PARSED/PARSE_FAILED in InventoryResult); strict UTF-8 decoding of analyzed files is not implemented yet (harness uses Files.readString on trusted files). Other Java 21 preview features besides unnamed classes/variables and string templates are not individually tested. All JavaParser problems map to SYNTAX_ERROR, including preview features its validator rejects.
- Slice 1A: symlinks are skipped without being counted; the spec's workspace rules (Phase 7) reject them at extraction. UTF-8 validity is not checked in inventory (deferred to parsing, Phase 2). The 256 KiB cap applies only to .java files; the Blueprint's archive-level caps arrive with acquisition in Phase 7.
- Slice 1A walks into excluded directories and counts their files instead of skipping the subtree; fine for bounded workspaces, revisit if scans get slow.
- No application code exists. Nothing in this repository runs yet.
- Deferred by design until their phase: Spring Boot (4), PostgreSQL/JPA/Flyway (5), sessions/CSRF (6), GitHub acquisition (7), worker (8), React (9), Docker/CI (10).

## Concepts I can explain without assistance
- (developer fills this in; the learning gate is what the developer can explain, not what Claude generated)
- 2026-09-23 Phase 2: parsing checks grammar not meaning; visitor + super.visit; explicit vs implicit members; token-based ncloc/comment/blank; complexity rules (else/comparisons add 0, ternary and && add 1, bodyless method = null); null (not measured) vs 0 (measured, nothing).
- 2026-09-23 Phase 1: inventory lists files vs analysis opens them; try-with-resources calls close() even on error; null = not measured; interface lets a new workspace plug in with zero changes to SourceInventory; role rule (src/main → MAIN, src/test → TEST, else OTHER_SOURCE).
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
- 2026-09-23 Role classification matches `src/main` / `src/test` anywhere in the path (first occurrence wins), not only at the repository root. Reason: multi-module Maven repos (`module/src/main/java`) are common on GitHub. Alternative: root-anchored only, as Blueprint 9.2 literally reads (rejected: would label most multi-module code OTHER_SOURCE). Custom source roots remain V1.
- 2026-09-23 `SourceWorkspace` exposes only `Path root()`. Cleanup/close for temporary workspaces is deliberately not added until Phase 7 needs it.
- 2026-09-23 RENAMED `SourceRole` → `SourceScope` (Spec conflict: schema `source_scope`, API `sourceScope`). Resolved in favor of the spec before persistence exists.
- 2026-09-23 Compact/unnamed classes rejected by our own check because JavaParser 3.28.2 accepts them at JAVA_21 although they were a Java 21 preview (JEP 445). Blueprint 9.4: unsupported constructs must fail visibly.
- 2026-09-23 Parse diagnostics store code + line/column only; at most 10 per file with a total count (cap is my choice; Blueprint 9.1 only requires sanitizing).
- 2026-09-23 Declaration counting choices where Blueprint 9.3 is silent: annotation members (`String value();` in `@interface`) are not methods; methods inside enum constant bodies, local classes, and anonymous classes count toward the file's methodCount; enum constant bodies are not anonymous classes (only `new X() { }` object creations are); lambdas are counted but never executables.
- 2026-09-23 blankLines follows Blueprint 9.3 literally ("lines containing only whitespace; separate from token metrics"): measured on raw text, so empty lines inside block comments or text blocks are blank AND comment/code. Whitespace = space, tab, form feed (JLS 3.6).
- 2026-09-23 TODO/FIXME "whole word" uses Java regex `\b` with default (ASCII) word characters: letters, digits, underscore. So `TODO_LATER` is not a marker.
- 2026-09-23 Method signatures use JavaParser's normalized type rendering (`Type.asString()`), not raw source text, so whitespace/comments inside types do not change identity. Blueprint 9.5 says "declared parameter type text"; this is the declared type, normalized.
- 2026-09-23 Owner label convention (Blueprint 9.3 requires only "deterministic"): "." for member types, "#" for local types, anonymous bodies (`#anonymous@<line of new>`), and enum constant bodies (`#CONSTANT`). Compact constructor signature = record component types.
- 2026-09-23 Method range begins at the first annotation/modifier; a preceding Javadoc is not included. methodNcloc includes lines of local/anonymous classes inside the body.
- 2026-09-23 Invalid UTF-8 → PARSE_FAILED with ENCODING_ERROR (kept in the coverage denominator), not SKIPPED. Blueprint 9.2 requires encoding problems to remain visible in coverage; SKIPPED is reserved for configured limits.
- 2026-09-23 Inventory uses `Files.walk` + try-with-resources (teaches resource closing, the Phase 1 gate). Alternative `Files.walkFileTree` with SKIP_SUBTREE avoids descending into excluded folders; deferred.

- 2026-09-25 Only top-level types are indexed in `explicit-import-v1`; an import of a nested type stays UNRESOLVED_OR_EXTERNAL. Blueprint 9.6 says "canonical type name" without deciding nested types; indexing member types would require walking every type body and handling shadowing, deferred to V2 with the symbol solver.
- 2026-09-25 `import static a.B.*;` is counted as static (not wildcard); each import lands in exactly one bucket so the evidence adds up.
- 2026-09-25 A self-import (file imports its own type) counts as resolved but creates no edge (Blueprint 9.6 "ignore self-file edges").
- 2026-09-25 Edge evidence caps `importedTypes` and `importLines` at 20 each with one `truncated` flag (API caps `importedTypes` at 20; capping lines too keeps evidence bounded). `importLines` are distinct ascending lines, so a duplicate import shows as two lines.
- 2026-09-25 `ImportGraph` is a final class with private adjacency maps (built once in the constructor), not a record, following the `ParseOutcome` precedent for derived internal state. Fan values for non-parsed files are `null` (not measured), matching the schema CHECK that `observed_fan_*` is NOT NULL only when PARSED.
- 2026-09-25 `AnalysisResult` validates that the graph's node set equals its parsed files. The graph is built by the analyzer, not inside the record, so the linking step is visible in the pipeline.

## Current learning gate (Phase 3, open)
- Developer must: draw A→B for a three-file example and state which way to walk for impact; derive every structural-v1 factor by hand (Slice 3B); name at least three things the import graph cannot observe. Interview: why an import graph is not a call graph or a DI graph.
- 2026-09-25 Slice 3A answers, first attempt: Q1 one edge per file pair — correct; said the two line numbers "get removed" — wrong (kept as `importLines [2, 4]` evidence). Q2 failed file "got removed" — wrong (still in the result as PARSE_FAILED; not a node because nothing was measured; its types are never indexed so imports of them are unresolved); "empty file gives 0" — correct. Q3 "for easy handling" — wrong (speed: one lookup per import, O(types + imports) vs files × imports). Re-taught with a two-file index walk-through and a 10,000,000 vs 11,000 comparison table.
- 2026-09-25 Multiple-choice retry: b, b, b — all correct (lines kept as evidence; null = not measured, never 0; map makes each import one lookup). Cart/Item/CartTest prediction skipped at developer's request ("commit and push 3A, continue with 3B and 3C"); recorded as the developer's decision. Slice 3A gate: questions met, prediction not attempted.

## Current learning gate (Phase 2, passed 2026-09-23) — exercise: docs/learning/phase-2-gate-Cart.java
- Developer must: manually predict metrics for a new ~20-line fixture, explain every complexity increment, distinguish unsupported/failed data from zero; interview: how parsing differs from compilation.

- 2026-09-23 Slice 2A answers: predictions 1 (undefined `hello`) PARSED and 3 (empty) PARSED correct; 2 (missing `}`) predicted PARSED, actually PARSE_FAILED (verified with ParseMain). Said "parsing does not check grammar" (backwards: it checks grammar, not meaning). Tokens vs tree: correct. Why discard recovered tree: did not know; re-taught. Why parsing is safe: correct (running could delete files/steal secrets).

- 2026-09-23 Grammar-vs-meaning check: answered "parsed"; `int y = 5 }` (missing `;`) is actually PARSE_FAILED at col 32, `new Banana()` (undefined type) PARSED. Why discard half-tree: "if something is missing we can't move further, tree stops" — partially right; missing the point that metrics from a partial tree would be wrong but look real. Concept still shaky; revisit with the first 2B fixture (a broken file must give no counts, not smaller counts).

- 2026-09-23 Slice 2B prediction (Shape interface + Circle record): interface 1, record 1, method 3, constructor 0 all correct (verified with ParseMain); executable (2) not answered. Understanding answers: (1) missing super.visit → "runs the code in the same loop" (wrong: the walk stops, children are never visited); (2) record 0 methods → "because there is break" (wrong: the methods are implicit, generated by Java, and only written ones count); (3) abstract not executable → "because of ; semicolon" (correct: no body). Re-taught 1 and 2; multiple-choice retry: both correct (b, b).

- 2026-09-23 Slice 2C prediction: ncloc 2 (actual 3, missed a code line), comment 2 and blank 1 correct (verified with ParseMain). Q1 string literal not a comment: correct. Q2 Set avoids duplicates: correct. Q3 code+comment line: "because of //" — partially right (code first, then a // comment on the same line).

- 2026-09-23 Slice 2D answers: string token not searched; `\b` word boundary; regex fooled by nesting/generics/strings — all correct.

- 2026-09-23 Slice 2E answers: Q1 overloads would overwrite each other — correct. Q2 missing pop → "not get the data" — vague; correct answer: old owner stays on the stack so later methods get wrong owner labels. Q3 `#` = local class inside a method — correct (not importable). Bank prediction skipped at developer's request ("commit and continue"); Q2 re-explained with a two-class stack trace.

- 2026-09-23 Slice 2F answers: Q1 grouped case adds 1 "because of the new style" — partial (one entry = one branch, however many labels). Q2 abstract → null "because every method is abstract" — not understood; re-taught (no body = nothing to measure; 1 would claim a path exists). Q3 and the `ship` prediction (actual 7, verified) not answered; walked through step by step, smaller practice `small(int, boolean)` (actual 3) given.

- 2026-09-23 Complexity practice: `small` answered 2 (actual 3, missed the ternary); re-taught with a symbol checklist; lambda-as-separate-unit re-taught with a deferred Runnable example. `ok(int, boolean)` answered 3 — correct (1 + if + &&).

- 2026-09-23 Phase 2 gate, first attempt on phase-2-gate-Cart.java: correct class 1, interface 1, constructor 1, executable 2, comment 1, blank 4, TODO 1; wrong methods (1, actual 2), total() complexity (7, actual 5), applies() complexity ("a number", actual null), ncloc (18, actual 17). Re-taught: bodyless interface method counts as method with null complexity; else and comparisons add 0.
- 2026-09-23 Gate retry: methods 2, applies null, else adds 0, ternary `?` adds 1 (not `>`) — all correct. Final: Q6 failed file null / parsed-without-bodies 0 — correct (b); Q7 parser checks grammar only — correct (b).
  - Phase 2 gate status: MET on 2026-09-23.

## Current learning gate (Phase 1, passed 2026-09-23)
- Developer must explain: what inventory counts vs what Java analysis does; why Files.walk is closed (try-with-resources, OS handles, even on exception); how paths are normalized and exclusions applied; when to use a checked exception.

- 2026-09-23 first attempt at Slice 1A questions:
  - Inventory vs analysis: half right (inventory lists folders/files); missing that analysis reads and parses file contents.
  - Why handles are returned on exception: NOT understood ("OS gets limited time"). Re-taught with a Door AutoCloseable demo (open → work → throw → close runs → catch).
  - Why relative paths: mixed up with "Path does not touch the disk". Re-taught: same result on every computer, no private laptop path leaked.
  - Prediction for the 4-file folder: not answered yet.
  - Second attempt: Q1 correct (lists vs opens). Q2 named try-with-resources; corrected that it calls close(). Q3 and the 4-file prediction left unanswered; developer said "I got the topic" and asked to continue with 1B. Recorded as the developer's decision; revisit Q3 at the Phase 1 checkpoint.
  - Gate status: partially met; proceeding at developer's request.
- 2026-09-23 Phase 1 checkpoint answers:
  - Relative paths: gave portability ("every laptop has its own full path"); privacy reason still missing.
  - null vs 0: correct (not measured, so null).
  - What changes in SourceInventory in Phase 7: not yet understood (answered about testability). Correct answer: nothing changes; the new workspace implements SourceWorkspace. Re-taught in session.
  - Role prediction (FooTest / app Foo / scripts Build): not answered.
  - Developer said they did not know Q3 or the role prediction. Re-taught with a PowerSource/Battery/SolarPanel interface demo and a step-by-step walk through the role rule. Gate still open.
  - Retry with choices: Q3 = b (zero lines change; new workspace plugs into the interface) correct; `app/src/main/java/Foo.java` = MAIN correct; `scripts/Build.java` = OTHER_SOURCE correct.
  - Phase 1 gate status: MET on 2026-09-23 (privacy reason for relative paths was taught, not independently stated).
- 2026-09-23 Slice 1B prediction answered correctly: `hi\r\n\r\n` = 2, `\r\n` = 1, `a\nb` = 2.

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
- Phase 3, slice 3B: `structural-v1` risk factors (Blueprint 10.1): `u(x; low, high)` normalization, five weighted factors (maxMethodComplexity, fileNcloc, maxMethodNcloc, observedFanOut, observedFanIn), unrounded sum, final round-half-up, priority bands (10.2 base bands only; overrides and findings are 3C). Per-factor evidence record (raw, low, high, normalized, weight, contribution). Tests: the exact 59-point PaymentService example, every band boundary (24/25, 49/50, 74/75), clamping at both ends, monotonicity, null score for PARSE_FAILED. Concept lesson first: piecewise linear normalization, why round only once, BigDecimal vs double for a 0–100 score.

## Suggested commit
- `feat(engine): build the explicit-import-v1 file graph with import evidence` — body: ImportCollector records declared top-level types and raw imports per parsed file; ImportGraphBuilder indexes types in one pass and resolves single-type non-static imports with one lookup each into unique directed FileEdges (duplicates and multi-type imports collapsed, self edges ignored, ambiguous names reported not guessed). ImportGraph keeps sorted edges, two adjacency lists for observed fan-out/fan-in (null for unparsed files), and per-file ImportEvidence whose five buckets must add up. AnalyzeMain prints the graph; 14 new tests including a hand-drawn four-edge fixture and two mutation checks.

