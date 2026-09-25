package dev.codepulse;

import dev.codepulse.engine.AnalysisResult;
import dev.codepulse.engine.FileEdge;
import dev.codepulse.engine.ImportEvidence;
import dev.codepulse.engine.ImportGraph;
import dev.codepulse.engine.JavaFileAnalysis;
import dev.codepulse.engine.JavaSourceAnalyzer;
import dev.codepulse.engine.LocalDirectoryWorkspace;
import dev.codepulse.engine.ParseDiagnostic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Developer-only harness: analyze every Java file under a trusted local folder and print a summary.
 * Never exposed over HTTP.
 *
 * <pre>java -cp "target/classes:$(cat target/classpath.txt)" dev.codepulse.AnalyzeMain src</pre>
 */
public final class AnalyzeMain {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: AnalyzeMain <directory>");
            System.exit(2);
        }
        AnalysisResult result = new JavaSourceAnalyzer().analyze(new LocalDirectoryWorkspace(Path.of(args[0])));

        ImportGraph graph = result.importGraph();
        System.out.printf("%-58s %-12s %-12s %6s %8s %6s %6s %6s%n",
            "file", "scope", "status", "ncloc", "methods", "maxCx", "fanOut", "fanIn");
        for (JavaFileAnalysis f : result.javaFiles()) {
            if (f.lines() == null) {
                StringBuilder why = new StringBuilder();
                for (ParseDiagnostic d : f.diagnostics()) {
                    why.append(d.code()).append(d.line() == null ? "" : "@" + d.line()).append(' ');
                }
                System.out.printf("%-58s %-12s %-12s %s%n", f.relativePath(), f.scope(), f.parseStatus(), why.toString().trim());
            } else {
                System.out.printf("%-58s %-12s %-12s %6d %8d %6d %6d %6d%n", f.relativePath(), f.scope(), f.parseStatus(),
                    f.lines().ncloc(), f.methods().size(), f.maxMethodComplexity(),
                    graph.observedFanOut(f.relativePath()), graph.observedFanIn(f.relativePath()));
            }
        }
        System.out.println();
        System.out.println("java files: " + result.eligibleFileCount()
            + "  parsed: " + result.parsedFileCount()
            + "  failed: " + result.parseFailedFileCount());
        System.out.println("parse coverage: " + (result.parseCoverage() == null
            ? "unknown (no Java files)" : String.format("%.1f%%", result.parseCoverage() * 100)));
        System.out.println("excluded files (not analyzed): " + result.inventory().excludedFileCount());

        printImportGraph(graph);
    }

    private static void printImportGraph(ImportGraph graph) {
        System.out.println();
        System.out.println("observed import edges (" + graph.graphMode() + "): " + graph.edgeCount());
        for (FileEdge edge : graph.edges()) {
            System.out.printf("  %s -> %s  %s lines %s%s%n", edge.sourcePath(), edge.targetPath(),
                edge.importedTypes(), edge.importLines(), edge.truncated() ? " (truncated)" : "");
        }

        int total = 0;
        int resolved = 0;
        int wildcard = 0;
        int statics = 0;
        int ambiguous = 0;
        int unresolved = 0;
        for (ImportEvidence e : graph.evidenceByFile().values()) {
            total += e.totalImportDeclarations();
            resolved += e.resolvedSingleTypeImports();
            wildcard += e.wildcardImports();
            statics += e.staticImports();
            ambiguous += e.ambiguousImports();
            unresolved += e.unresolvedOrExternalImports();
        }
        System.out.println("import declarations: " + total + "  resolved: " + resolved + "  wildcard: " + wildcard
            + "  static: " + statics + "  ambiguous: " + ambiguous + "  unresolved/external: " + unresolved);

        if (!graph.ambiguousTypes().isEmpty()) {
            System.out.println("WARNING ambiguous type names (declared in more than one file; no edges created):");
            for (Map.Entry<String, List<String>> entry : graph.ambiguousTypes().entrySet()) {
                System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
            }
        }
        System.out.println("note: wildcard, static, same-package, and nested-type imports are not resolved;");
        System.out.println("      fan-in/fan-out are lower bounds from explicit imports, not a complete dependency graph.");
    }
}
