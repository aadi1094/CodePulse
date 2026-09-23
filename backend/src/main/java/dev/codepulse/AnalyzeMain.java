package dev.codepulse;

import dev.codepulse.engine.AnalysisResult;
import dev.codepulse.engine.JavaFileAnalysis;
import dev.codepulse.engine.JavaSourceAnalyzer;
import dev.codepulse.engine.LocalDirectoryWorkspace;
import dev.codepulse.engine.ParseDiagnostic;

import java.io.IOException;
import java.nio.file.Path;

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

        System.out.printf("%-58s %-12s %-12s %6s %8s %6s%n", "file", "scope", "status", "ncloc", "methods", "maxCx");
        for (JavaFileAnalysis f : result.javaFiles()) {
            if (f.lines() == null) {
                StringBuilder why = new StringBuilder();
                for (ParseDiagnostic d : f.diagnostics()) {
                    why.append(d.code()).append(d.line() == null ? "" : "@" + d.line()).append(' ');
                }
                System.out.printf("%-58s %-12s %-12s %s%n", f.relativePath(), f.scope(), f.parseStatus(), why.toString().trim());
            } else {
                System.out.printf("%-58s %-12s %-12s %6d %8d %6d%n", f.relativePath(), f.scope(), f.parseStatus(),
                    f.lines().ncloc(), f.methods().size(), f.maxMethodComplexity());
            }
        }
        System.out.println();
        System.out.println("java files: " + result.eligibleFileCount()
            + "  parsed: " + result.parsedFileCount()
            + "  failed: " + result.parseFailedFileCount());
        System.out.println("parse coverage: " + (result.parseCoverage() == null
            ? "unknown (no Java files)" : String.format("%.1f%%", result.parseCoverage() * 100)));
        System.out.println("excluded files (not analyzed): " + result.inventory().excludedFileCount());
    }
}
