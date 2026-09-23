package dev.codepulse.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds a tiny fake project in a temporary folder:
 * <pre>
 *   Default.java                      default package, OTHER_SOURCE      -> PARSED
 *   README.md                         not Java                           -> not analyzed
 *   target/Gen.java                   excluded directory                 -> not analyzed
 *   src/main/java/app/BadBytes.java   contains byte 0xFF (not UTF-8)     -> PARSE_FAILED encoding
 *   src/main/java/app/Broken.java     class never closed                 -> PARSE_FAILED syntax
 *   src/main/java/app/Good.java       one method with a ternary          -> PARSED
 *   src/test/java/app/GoodTest.java   empty test class                   -> PARSED
 * </pre>
 * Eligible Java files 5, parsed 3, failed 2, coverage 3/5 = 0.6.
 */
class JavaSourceAnalyzerTest {

    // Computed with the macOS tool:  printf 'class A {}\n' | shasum -a 256
    private static final String SHA256_OF_CLASS_A = "f119fc42a923d52cbd5420b0c5841969bef8dea5e8b78ba392ffb58312380247";
    // printf '' | shasum -a 256   (the well-known SHA-256 of zero bytes)
    private static final String SHA256_OF_EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @TempDir
    Path root;

    private final JavaSourceAnalyzer analyzer = new JavaSourceAnalyzer();

    private void writeFakeProject() throws IOException {
        write("Default.java", "class A {}\n");
        write("README.md", "# readme\n");
        write("target/Gen.java", "class Gen {}\n");
        ByteArrayOutputStream bad = new ByteArrayOutputStream();
        bad.writeBytes("class Bad {} // ".getBytes(StandardCharsets.UTF_8));
        bad.write(0xFF);                                    // never valid in UTF-8
        bad.write('\n');
        writeBytes("src/main/java/app/BadBytes.java", bad.toByteArray());
        write("src/main/java/app/Broken.java", "package app;\nclass Broken {\n");
        write("src/main/java/app/Good.java", """
            package app;

            public class Good {
                int twice(int x) {
                    return x > 0 ? x * 2 : 0;
                }
            }
            """);
        write("src/test/java/app/GoodTest.java", "package app;\n\nclass GoodTest {}\n");
    }

    @Test
    void analyzesEveryEligibleJavaFileInPathOrderWithCoverage() throws IOException {
        writeFakeProject();

        AnalysisResult result = analyzer.analyze(new LocalDirectoryWorkspace(root));

        List<String> summary = new ArrayList<>();
        for (JavaFileAnalysis f : result.javaFiles()) {
            summary.add(f.relativePath() + " " + f.scope() + " " + f.parseStatus());
        }
        assertEquals(List.of(
            "Default.java OTHER_SOURCE PARSED",
            "src/main/java/app/BadBytes.java MAIN PARSE_FAILED",
            "src/main/java/app/Broken.java MAIN PARSE_FAILED",
            "src/main/java/app/Good.java MAIN PARSED",
            "src/test/java/app/GoodTest.java TEST PARSED"), summary);
        assertEquals(5, result.eligibleFileCount());
        assertEquals(3, result.parsedFileCount());
        assertEquals(2, result.parseFailedFileCount());
        assertEquals(0.6, result.parseCoverage(), 1e-9);
        assertEquals(1, result.inventory().excludedFileCount());       // target/Gen.java
    }

    @Test
    void aParsedFileCarriesAllItsMeasurements() throws IOException {
        writeFakeProject();

        JavaFileAnalysis good = find(analyzer.analyze(new LocalDirectoryWorkspace(root)), "src/main/java/app/Good.java");

        // Good.java, 7 lines:  1 package app;  2 (blank)  3 public class Good {  4 int twice(int x) {
        //                      5 return x > 0 ? x * 2 : 0;  6 }  7 }
        assertEquals("app", good.packageName());
        assertEquals(7, good.physicalLoc());
        assertEquals(new LineMetrics(6, 0, 1), good.lines());
        assertEquals(1, good.declarations().classCount());
        assertEquals(List.of(new MethodMeasurement("Good", "twice(int)", DeclarationKind.METHOD,
            true, 4, 6, 3, 2)), good.methods());                     // complexity 1 + ternary = 2
        assertEquals(2, good.maxMethodComplexity());
        assertEquals(new CommentMarkers(0, 0), good.markers());
    }

    @Test
    void failedFilesHaveNoMetricsOnlyADiagnostic() throws IOException {
        writeFakeProject();
        AnalysisResult result = analyzer.analyze(new LocalDirectoryWorkspace(root));

        JavaFileAnalysis badBytes = find(result, "src/main/java/app/BadBytes.java");
        assertEquals(List.of(new ParseDiagnostic(DiagnosticCode.ENCODING_ERROR, null, null)), badBytes.diagnostics());

        JavaFileAnalysis broken = find(result, "src/main/java/app/Broken.java");
        assertEquals(DiagnosticCode.SYNTAX_ERROR, broken.diagnostics().get(0).code());

        for (JavaFileAnalysis failed : List.of(badBytes, broken)) {
            assertNull(failed.lines(), "not measured, so null, never 0");
            assertNull(failed.declarations());
            assertNull(failed.maxMethodComplexity());
            assertNull(failed.packageName());
            assertTrue(failed.methods().isEmpty());
            assertEquals(64, failed.contentSha256().length(), "the bytes are still fingerprinted");
        }
    }

    @Test
    void aParsedFileWithoutMethodsHasMaxComplexityZeroNotNull() throws IOException {
        writeFakeProject();

        JavaFileAnalysis test = find(analyzer.analyze(new LocalDirectoryWorkspace(root)), "src/test/java/app/GoodTest.java");

        assertEquals(ParseStatus.PARSED, test.parseStatus());
        assertEquals(0, test.maxMethodComplexity());
    }

    @Test
    void contentHashMatchesAnIndependentTool() throws IOException {
        write("Default.java", "class A {}\n");
        write("Empty.java", "");

        AnalysisResult result = analyzer.analyze(new LocalDirectoryWorkspace(root));

        assertEquals(SHA256_OF_CLASS_A, find(result, "Default.java").contentSha256());
        JavaFileAnalysis empty = find(result, "Empty.java");
        assertEquals(SHA256_OF_EMPTY, empty.contentSha256());
        assertEquals(ParseStatus.PARSED, empty.parseStatus());        // an empty file is valid Java
        assertEquals(new LineMetrics(0, 0, 0), empty.lines());
    }

    @Test
    void workspaceWithoutJavaFilesHasUnknownCoverageNotZero() throws IOException {
        write("README.md", "no java here\n");

        AnalysisResult result = analyzer.analyze(new LocalDirectoryWorkspace(root));

        assertEquals(0, result.eligibleFileCount());
        assertNull(result.parseCoverage(), "0 of 0 is unknown, not 0% and not 100%");
    }

    @Test
    void strictUtf8AcceptsRealUtf8AndRejectsInvalidBytes() {
        assertEquals("héllo 日本", JavaSourceAnalyzer.decodeStrictUtf8("héllo 日本".getBytes(StandardCharsets.UTF_8)));
        assertNull(JavaSourceAnalyzer.decodeStrictUtf8(new byte[] {'a', (byte) 0xFF}));
        // 0xE9 alone is "é" in Latin-1 but an incomplete sequence in UTF-8
        assertNull(JavaSourceAnalyzer.decodeStrictUtf8(new byte[] {'c', 'a', 'f', (byte) 0xE9}));
    }

    private static JavaFileAnalysis find(AnalysisResult result, String path) {
        for (JavaFileAnalysis f : result.javaFiles()) {
            if (f.relativePath().equals(path)) {
                return f;
            }
        }
        throw new AssertionError("not analyzed: " + path);
    }

    private void write(String relativePath, String content) throws IOException {
        writeBytes(relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytes(String relativePath, byte[] content) throws IOException {
        Path file = root.resolve(relativePath);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, content);
    }
}
