package dev.codepulse.engine;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * The engine's front door: analyzes every Java file in a workspace (Blueprint 9.1 pipeline).
 *
 * <pre>
 * workspace -> inventory -> for each .java file (in path order):
 *     bounded read -> SHA-256 -> strict UTF-8 -> parse -> measure (only if PARSED)
 *   then: index declared types -> build explicit import graph
 * </pre>
 *
 * <p>One file is processed at a time and its syntax tree is dropped as soon as its numbers are
 * collected, so memory does not grow with the number of files. No Spring, no database, no network:
 * source in, immutable result out. The analyzed code is only read, never compiled or run.
 *
 * <p>Not thread-safe (it owns one parser). Create one per analysis run.
 */
public final class JavaSourceAnalyzer {

    private final SourceInventory inventory;
    private final JavaSourceParser parser = new JavaSourceParser();
    private final long maxJavaFileBytes;

    public JavaSourceAnalyzer() {
        this(SourceInventory.DEFAULT_MAX_JAVA_FILE_BYTES);
    }

    public JavaSourceAnalyzer(long maxJavaFileBytes) {
        this.inventory = new SourceInventory(SourceInventory.DEFAULT_EXCLUDED_DIRECTORIES, maxJavaFileBytes);
        this.maxJavaFileBytes = maxJavaFileBytes;
    }

    /**
     * @throws FileSizeLimitExceededException if any Java file is over the limit (the whole run fails)
     * @throws IOException                    if the workspace cannot be read
     */
    public AnalysisResult analyze(SourceWorkspace workspace) throws IOException {
        InventoryResult files = inventory.scan(workspace);
        List<JavaFileAnalysis> javaFiles = new ArrayList<>();
        for (SourceFile file : files.files()) {
            if (file.isJava()) {
                javaFiles.add(analyzeFile(workspace.root(), file));
            }
        }
        // Linking step (Blueprint 9.1): needs every file's declared types, so it runs after the loop.
        return new AnalysisResult(files, javaFiles, ImportGraphBuilder.build(javaFiles));
    }

    private JavaFileAnalysis analyzeFile(Path root, SourceFile file) throws IOException {
        byte[] bytes = readBounded(root.resolve(file.relativePath()), file.relativePath());
        String sha256 = sha256Hex(bytes);

        String text = decodeStrictUtf8(bytes);
        if (text == null) {
            return JavaFileAnalysis.failed(file, sha256,
                List.of(new ParseDiagnostic(DiagnosticCode.ENCODING_ERROR, null, null)));
        }

        ParseOutcome outcome = parser.parse(text);
        if (outcome.syntaxTree().isEmpty()) {
            return JavaFileAnalysis.failed(file, sha256, outcome.diagnostics());
        }
        CompilationUnit tree = outcome.syntaxTree().get();
        String packageName = tree.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
        return JavaFileAnalysis.parsed(file, sha256, packageName,
            ImportCollector.declaredTypeNames(tree),
            ImportCollector.imports(tree),
            DeclarationCounter.countIn(tree),
            LineMetricsCalculator.calculate(tree, text),
            CommentMarkerCounter.count(tree),
            MethodCollector.collect(tree));
        // "tree" goes out of scope here and can be garbage-collected before the next file.
    }

    /**
     * Reads at most maxBytes + 1 bytes. Inventory already checked the size, but the file could have
     * grown since then, so the limit is enforced again on the bytes actually read.
     */
    private byte[] readBounded(Path path, String relativePath) throws IOException {
        try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(Math.toIntExact(maxJavaFileBytes + 1));
            if (bytes.length > maxJavaFileBytes) {
                throw new FileSizeLimitExceededException(relativePath, maxJavaFileBytes);
            }
            return bytes;
        }
    }

    /**
     * @return the text, or {@code null} if the bytes are not valid UTF-8. REPORT makes the decoder
     *         fail instead of silently replacing bad bytes with '?' characters.
     */
    static String decodeStrictUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /** SHA-256 fingerprint of the exact bytes, as 64 lowercase hex characters. */
    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime is required to support SHA-256, so this cannot happen.
            throw new IllegalStateException(e);
        }
    }
}
