package dev.codepulse.engine;

import java.util.List;
import java.util.Objects;

/**
 * Everything CodePulse measured about one eligible Java file.
 *
 * <p>Inventory facts (path, scope, size, physical lines, content hash) are always present. Structural
 * metrics exist only for a PARSED file; for PARSE_FAILED they are {@code null}, meaning "not
 * measured". They are never 0, because 0 would claim "measured, and there was nothing".
 *
 * <p>Build instances with {@link #parsed} or {@link #failed}, which keep the two shapes consistent.
 *
 * @param relativePath        POSIX path from the workspace root
 * @param scope               MAIN, TEST, or OTHER_SOURCE
 * @param sizeBytes           exact byte size
 * @param physicalLoc         physical lines (from inventory)
 * @param contentSha256       lowercase hex SHA-256 of the exact bytes analyzed
 * @param parseStatus         PARSED or PARSE_FAILED
 * @param diagnostics         sanitized problems; empty when PARSED
 * @param packageName         declared package, "" for the default package; null if not parsed
 * @param declaredTypeNames   fully qualified top-level type names, in source order; empty if not parsed
 * @param imports             every import declaration as written; empty if not parsed
 * @param declarations        null if not parsed
 * @param lines               null if not parsed
 * @param markers             null if not parsed
 * @param methods             methods and constructors; empty if not parsed (check parseStatus)
 * @param maxMethodComplexity highest method complexity, 0 when there are no bodies; null if not parsed
 */
public record JavaFileAnalysis(
    String relativePath,
    SourceScope scope,
    long sizeBytes,
    int physicalLoc,
    String contentSha256,
    ParseStatus parseStatus,
    List<ParseDiagnostic> diagnostics,
    String packageName,
    List<String> declaredTypeNames,
    List<ImportStatement> imports,
    DeclarationCounts declarations,
    LineMetrics lines,
    CommentMarkers markers,
    List<MethodMeasurement> methods,
    Integer maxMethodComplexity) {

    public JavaFileAnalysis {
        Objects.requireNonNull(relativePath);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(contentSha256);
        Objects.requireNonNull(parseStatus);
        diagnostics = List.copyOf(diagnostics);
        declaredTypeNames = List.copyOf(declaredTypeNames);
        imports = List.copyOf(imports);
        methods = List.copyOf(methods);
        boolean parsed = parseStatus == ParseStatus.PARSED;
        boolean hasMetrics = packageName != null && declarations != null && lines != null
            && markers != null && maxMethodComplexity != null;
        boolean hasNoMetrics = packageName == null && declarations == null && lines == null
            && markers == null && maxMethodComplexity == null && methods.isEmpty()
            && declaredTypeNames.isEmpty() && imports.isEmpty();
        if (parsed ? !(hasMetrics && diagnostics.isEmpty()) : !(hasNoMetrics && !diagnostics.isEmpty())) {
            throw new IllegalArgumentException("metrics must exist exactly when the file parsed: " + relativePath);
        }
    }

    /**
     * @return the longest method's ncloc, 0 when the file has no methods; {@code null} if not parsed.
     *         Derived from {@link #methods()} so it can never disagree with them.
     */
    public Integer maxMethodNcloc() {
        if (parseStatus() != ParseStatus.PARSED) {
            return null;
        }
        int max = 0;
        for (MethodMeasurement method : methods) {
            max = Math.max(max, method.ncloc());
        }
        return max;
    }

    static JavaFileAnalysis parsed(SourceFile file, String sha256, String packageName,
                                   List<String> declaredTypeNames, List<ImportStatement> imports,
                                   DeclarationCounts declarations, LineMetrics lines,
                                   CommentMarkers markers, List<MethodMeasurement> methods) {
        return new JavaFileAnalysis(file.relativePath(), file.scope(), file.sizeBytes(), file.physicalLines(),
            sha256, ParseStatus.PARSED, List.of(), packageName, declaredTypeNames, imports, declarations, lines,
            markers, methods, ComplexityCalculator.maxMethodComplexity(methods));
    }

    static JavaFileAnalysis failed(SourceFile file, String sha256, List<ParseDiagnostic> diagnostics) {
        return new JavaFileAnalysis(file.relativePath(), file.scope(), file.sizeBytes(), file.physicalLines(),
            sha256, ParseStatus.PARSE_FAILED, diagnostics, null, List.of(), List.of(), null, null, null,
            List.of(), null);
    }
}
