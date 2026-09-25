package dev.codepulse.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The {@code findings-v1} rule set (API contract "Finding rules"). Each rule is a fixed threshold
 * on an already measured value; nothing is re-parsed here.
 *
 * <p>A threshold here need not match where the score starts rising. Complexity 20 triggers a
 * finding while the score already grows from 11: the score orders files smoothly, the finding
 * points at one method. Both are explained, neither is hidden.
 *
 * <p>Pure: one file's analysis and its import evidence in, a sorted list of findings out.
 */
public final class FindingRules {

    public static final String RULES_VERSION = "findings-v1";

    static final int HIGH_METHOD_COMPLEXITY = 20;
    static final int EXTREME_METHOD_COMPLEXITY = 30;
    static final int LONG_METHOD_NCLOC = 80;
    static final int LARGE_FILE_NCLOC = 1000;
    static final int VERY_LARGE_FILE_NCLOC = 2000;

    private FindingRules() {
    }

    /** @param evidence the file's import evidence, or {@code null} for an unparsed file */
    public static List<Finding> evaluate(JavaFileAnalysis file, ImportEvidence evidence) {
        List<Finding> findings = new ArrayList<>();
        String path = file.relativePath();

        if (file.parseStatus() != ParseStatus.PARSED) {
            ParseDiagnostic first = file.diagnostics().get(0);
            Map<String, Object> details = new TreeMap<>();
            details.put("diagnosticCode", first.code().name());
            details.put("diagnosticCount", file.diagnostics().size());
            if (first.line() != null) {
                details.put("line", first.line());
            }
            findings.add(new Finding(path, "PARSE_FAILED", FindingSeverity.WARNING, null, null,
                "The file could not be parsed (" + first.code() + "), so no structural metrics or score exist for it.", details));
            return List.copyOf(findings);      // nothing else is measurable
        }

        for (MethodMeasurement method : file.methods()) {
            Map<String, Object> where = new TreeMap<>(Map.of(
                "ownerLabel", method.ownerLabel(), "signature", method.signature()));
            if (method.complexity() != null && method.complexity() >= HIGH_METHOD_COMPLEXITY) {
                boolean extreme = method.complexity() >= EXTREME_METHOD_COMPLEXITY;
                Map<String, Object> details = new TreeMap<>(where);
                details.put("complexity", method.complexity());
                details.put("threshold", extreme ? EXTREME_METHOD_COMPLEXITY : HIGH_METHOD_COMPLEXITY);
                findings.add(new Finding(path, extreme ? "EXTREME_METHOD_COMPLEXITY" : "HIGH_METHOD_COMPLEXITY",
                    extreme ? FindingSeverity.HIGH : FindingSeverity.WARNING, method.beginLine(), method.endLine(),
                    method.signature() + " has complexity " + method.complexity() + " (threshold "
                        + (extreme ? EXTREME_METHOD_COMPLEXITY : HIGH_METHOD_COMPLEXITY) + ").", details));
            }
            if (method.ncloc() >= LONG_METHOD_NCLOC) {
                Map<String, Object> details = new TreeMap<>(where);
                details.put("ncloc", method.ncloc());
                details.put("threshold", LONG_METHOD_NCLOC);
                findings.add(new Finding(path, "LONG_METHOD", FindingSeverity.WARNING, method.beginLine(), method.endLine(),
                    method.signature() + " spans " + method.ncloc() + " source lines (threshold " + LONG_METHOD_NCLOC + ").", details));
            }
        }

        int ncloc = file.lines().ncloc();
        if (ncloc >= LARGE_FILE_NCLOC) {
            boolean veryLarge = ncloc >= VERY_LARGE_FILE_NCLOC;
            int threshold = veryLarge ? VERY_LARGE_FILE_NCLOC : LARGE_FILE_NCLOC;
            findings.add(new Finding(path, veryLarge ? "VERY_LARGE_FILE" : "LARGE_FILE",
                veryLarge ? FindingSeverity.HIGH : FindingSeverity.WARNING, null, null,
                "The file has " + ncloc + " source lines (threshold " + threshold + ").",
                Map.of("ncloc", ncloc, "threshold", threshold)));
        }

        CommentMarkers markers = file.markers();
        if (markers.todoCount() > 0) {
            findings.add(new Finding(path, "TODO_MARKER", FindingSeverity.INFO, null, null,
                markers.todoCount() + " TODO comment marker(s) found.", Map.of("count", markers.todoCount())));
        }
        if (markers.fixmeCount() > 0) {
            findings.add(new Finding(path, "FIXME_MARKER", FindingSeverity.WARNING, null, null,
                markers.fixmeCount() + " FIXME comment marker(s) found.", Map.of("count", markers.fixmeCount())));
        }

        if (evidence != null && evidence.unresolvedOrExternalImports() > 0) {
            findings.add(new Finding(path, "UNRESOLVED_IMPORTS", FindingSeverity.INFO, null, null,
                evidence.unresolvedOrExternalImports() + " import(s) could not be matched to a file in this snapshot "
                    + "(library, JDK, nested type, or unparsed source).",
                Map.of("count", evidence.unresolvedOrExternalImports(),
                    "examples", evidence.unresolvedExamples(),
                    "examplesTruncated", evidence.examplesTruncated())));
        }

        findings.sort(ORDER);
        return List.copyOf(findings);
    }

    /** Whole-file findings first (by rule), then method findings by line, then rule. */
    static final Comparator<Finding> ORDER = Comparator
        .comparing((Finding f) -> f.beginLine() == null ? 0 : f.beginLine())
        .thenComparing(Finding::ruleCode);
}
