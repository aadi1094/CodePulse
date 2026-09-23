package dev.codepulse.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each test gets a brand-new empty folder from JUnit (@TempDir), builds a tiny fixture tree in it,
 * and JUnit deletes the folder afterwards. Nothing touches your real files.
 */
class SourceInventoryTest {

    @TempDir
    Path root;

    private final SourceInventory inventory = new SourceInventory();

    @Test
    void emptyDirectoryHasNoFiles() throws IOException {
        InventoryResult result = inventory.scan(workspace(root));

        assertTrue(result.files().isEmpty());
        assertEquals(0, result.excludedFileCount());
    }

    @Test
    void listsNestedFilesSortedWithForwardSlashPaths() throws IOException {
        // Created in a scrambled order on purpose.
        write("src/test/java/AppTest.java", "class AppTest {}");
        write("README.md", "hi");
        write("src/main/java/App.java", "class App {}");

        InventoryResult result = inventory.scan(workspace(root));

        assertEquals(
            List.of("README.md", "src/main/java/App.java", "src/test/java/AppTest.java"),
            paths(result));
    }

    @Test
    void recordsExactByteSizes() throws IOException {
        write("five.txt", "hello");     // 5 bytes
        write("crlf.txt", "ab\r\ncd");  // 6 bytes: \r and \n are one byte each
        write("empty.txt", "");         // 0 bytes

        InventoryResult result = inventory.scan(workspace(root));

        assertEquals(List.of(
            SourceFile.other("crlf.txt", 6),
            SourceFile.other("empty.txt", 0),
            SourceFile.other("five.txt", 5)), result.files());
    }

    @Test
    void skipsExcludedDirectoriesButCountsTheirFiles() throws IOException {
        write("src/main/java/App.java", "class App {}");
        write("target/classes/App.class", "bytecode");
        write(".git/config", "[core]");
        write("frontend/node_modules/lib/index.js", "x");
        write("src/build/Generated.java", "class Generated {}");

        InventoryResult result = inventory.scan(workspace(root));

        assertEquals(List.of("src/main/java/App.java"), paths(result));
        assertEquals(4, result.excludedFileCount());
    }

    @Test
    void excludesOnlyExactDirectoryNamesNotSimilarNamesOrFileNames() throws IOException {
        write("builder/Tool.java", "class Tool {}");  // "builder" is not "build"
        write("src/target", "a file named target");  // a FILE called target is kept
        write("Target/Upper.java", "class Upper {}"); // matching is case-sensitive

        InventoryResult result = inventory.scan(workspace(root));

        assertEquals(List.of("Target/Upper.java", "builder/Tool.java", "src/target"), paths(result));
        assertEquals(0, result.excludedFileCount());
    }

    @Test
    void doesNotFollowOrListSymbolicLinks(@TempDir Path outside) throws IOException {
        Path secret = outside.resolve("secret.txt");
        Files.writeString(secret, "must not be inventoried");
        write("real.txt", "ok");
        Files.createSymbolicLink(root.resolve("link-to-file"), secret);
        Files.createSymbolicLink(root.resolve("link-to-dir"), outside);

        InventoryResult result = inventory.scan(workspace(root));

        assertEquals(List.of("real.txt"), paths(result));
    }

    @Test
    void countsLinesForJavaFilesOnly() throws IOException {
        // "class App {" is 11 bytes, "\n" 1, "}" 1, "\n" 1 -> 14 bytes, 2 lines
        write("src/App.java", "class App {\n}\n");
        write("README.md", "line one\nline two\n");

        InventoryResult result = inventory.scan(workspace(root));

        assertEquals(List.of(
            SourceFile.other("README.md", 18),          // not measured: null, not 0
            new SourceFile("src/App.java", 14, 2, SourceScope.OTHER_SOURCE)), result.files());
    }

    @Test
    void javaFileExactlyAtTheLimitIsAccepted() throws IOException {
        write("A.java", "0123456789");                  // exactly 10 bytes
        SourceInventory tenByteLimit = new SourceInventory(SourceInventory.DEFAULT_EXCLUDED_DIRECTORIES, 10);

        InventoryResult result = tenByteLimit.scan(workspace(root));

        assertEquals(List.of(new SourceFile("A.java", 10, 1, SourceScope.OTHER_SOURCE)), result.files());
    }

    @Test
    void javaFileOverTheLimitStopsTheScan() throws IOException {
        write("Big.java", "0123456789X");                // 11 bytes
        write("Small.java", "class S {}");
        SourceInventory tenByteLimit = new SourceInventory(SourceInventory.DEFAULT_EXCLUDED_DIRECTORIES, 10);

        FileSizeLimitExceededException ex =
            assertThrows(FileSizeLimitExceededException.class, () -> tenByteLimit.scan(workspace(root)));

        assertEquals("Big.java", ex.relativePath());
        assertEquals(10, ex.limitBytes());
    }

    @Test
    void largeNonJavaFilesAreNotLimited() throws IOException {
        write("image.png", "x".repeat(50));             // bigger than the limit, but not Java
        SourceInventory tenByteLimit = new SourceInventory(SourceInventory.DEFAULT_EXCLUDED_DIRECTORIES, 10);

        InventoryResult result = tenByteLimit.scan(workspace(root));

        assertEquals(List.of(SourceFile.other("image.png", 50)), result.files());
    }

    @Test
    void classifiesJavaFilesByScopeAndCountsThem() throws IOException {
        write("src/main/java/App.java", "class App {}");
        write("src/test/java/AppTest.java", "class AppTest {}");
        write("billing/src/main/java/Invoice.java", "class Invoice {}");   // multi-module: still MAIN
        write("tools/Gen.java", "class Gen {}");
        write("src/mainly/Odd.java", "class Odd {}");                        // "mainly" is not "main"
        write("src/main/resources/app.properties", "x=1");                   // not Java: no scope

        InventoryResult result = inventory.scan(workspace(root));

        assertEquals(List.of(
            new SourceFile("billing/src/main/java/Invoice.java", 16, 1, SourceScope.MAIN),
            new SourceFile("src/main/java/App.java", 12, 1, SourceScope.MAIN),
            SourceFile.other("src/main/resources/app.properties", 3),       // "java" < "resources"
            new SourceFile("src/mainly/Odd.java", 12, 1, SourceScope.OTHER_SOURCE),
            new SourceFile("src/test/java/AppTest.java", 16, 1, SourceScope.TEST),
            new SourceFile("tools/Gen.java", 12, 1, SourceScope.OTHER_SOURCE)), result.files());
        assertEquals(5, result.javaFileCount());
        assertEquals(1, result.otherFileCount());
        assertEquals(2, result.javaFileCount(SourceScope.MAIN));
        assertEquals(1, result.javaFileCount(SourceScope.TEST));
        assertEquals(2, result.javaFileCount(SourceScope.OTHER_SOURCE));
    }

    @Test
    void firstSrcMainOrSrcTestInThePathDecidesTheScope() throws IOException {
        // A fixture that lives inside test code is TEST, even though "src/main" appears later.
        write("src/test/resources/fixtures/src/main/Fixture.java", "class F {}");

        InventoryResult result = inventory.scan(workspace(root));

        assertEquals(SourceScope.TEST, result.files().get(0).scope());
    }

    @Test
    void rejectsMissingRoot() {
        assertThrows(NoSuchFileException.class, () -> inventory.scan(workspace(root.resolve("does-not-exist"))));
    }

    @Test
    void rejectsRootThatIsAFile() throws IOException {
        Path file = write("plain.txt", "x");

        assertThrows(NotDirectoryException.class, () -> inventory.scan(workspace(file)));
    }

    private static SourceWorkspace workspace(Path directory) {
        return new LocalDirectoryWorkspace(directory);
    }

    private Path write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static List<String> paths(InventoryResult result) {
        List<String> out = new ArrayList<>();
        for (SourceFile file : result.files()) {
            out.add(file.relativePath());
        }
        return out;
    }
}
