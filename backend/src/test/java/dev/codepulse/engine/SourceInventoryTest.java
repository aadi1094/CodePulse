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
        InventoryResult result = inventory.scan(root);

        assertTrue(result.files().isEmpty());
        assertEquals(0, result.excludedFileCount());
    }

    @Test
    void listsNestedFilesSortedWithForwardSlashPaths() throws IOException {
        // Created in a scrambled order on purpose.
        write("src/test/java/AppTest.java", "class AppTest {}");
        write("README.md", "hi");
        write("src/main/java/App.java", "class App {}");

        InventoryResult result = inventory.scan(root);

        assertEquals(
            List.of("README.md", "src/main/java/App.java", "src/test/java/AppTest.java"),
            paths(result));
    }

    @Test
    void recordsExactByteSizes() throws IOException {
        write("five.txt", "hello");     // 5 bytes
        write("crlf.txt", "ab\r\ncd");  // 6 bytes: \r and \n are one byte each
        write("empty.txt", "");         // 0 bytes

        InventoryResult result = inventory.scan(root);

        assertEquals(List.of(
            new SourceFile("crlf.txt", 6, null),
            new SourceFile("empty.txt", 0, null),
            new SourceFile("five.txt", 5, null)), result.files());
    }

    @Test
    void skipsExcludedDirectoriesButCountsTheirFiles() throws IOException {
        write("src/main/java/App.java", "class App {}");
        write("target/classes/App.class", "bytecode");
        write(".git/config", "[core]");
        write("frontend/node_modules/lib/index.js", "x");
        write("src/build/Generated.java", "class Generated {}");

        InventoryResult result = inventory.scan(root);

        assertEquals(List.of("src/main/java/App.java"), paths(result));
        assertEquals(4, result.excludedFileCount());
    }

    @Test
    void excludesOnlyExactDirectoryNamesNotSimilarNamesOrFileNames() throws IOException {
        write("builder/Tool.java", "class Tool {}");  // "builder" is not "build"
        write("src/target", "a file named target");  // a FILE called target is kept
        write("Target/Upper.java", "class Upper {}"); // matching is case-sensitive

        InventoryResult result = inventory.scan(root);

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

        InventoryResult result = inventory.scan(root);

        assertEquals(List.of("real.txt"), paths(result));
    }

    @Test
    void countsLinesForJavaFilesOnly() throws IOException {
        // "class App {" is 11 bytes, "\n" 1, "}" 1, "\n" 1 -> 14 bytes, 2 lines
        write("src/App.java", "class App {\n}\n");
        write("README.md", "line one\nline two\n");

        InventoryResult result = inventory.scan(root);

        assertEquals(List.of(
            new SourceFile("README.md", 18, null),     // not measured: null, not 0
            new SourceFile("src/App.java", 14, 2)), result.files());
    }

    @Test
    void javaFileExactlyAtTheLimitIsAccepted() throws IOException {
        write("A.java", "0123456789");                  // exactly 10 bytes
        SourceInventory tenByteLimit = new SourceInventory(SourceInventory.DEFAULT_EXCLUDED_DIRECTORIES, 10);

        InventoryResult result = tenByteLimit.scan(root);

        assertEquals(List.of(new SourceFile("A.java", 10, 1)), result.files());
    }

    @Test
    void javaFileOverTheLimitStopsTheScan() throws IOException {
        write("Big.java", "0123456789X");                // 11 bytes
        write("Small.java", "class S {}");
        SourceInventory tenByteLimit = new SourceInventory(SourceInventory.DEFAULT_EXCLUDED_DIRECTORIES, 10);

        FileSizeLimitExceededException ex =
            assertThrows(FileSizeLimitExceededException.class, () -> tenByteLimit.scan(root));

        assertEquals("Big.java", ex.relativePath());
        assertEquals(10, ex.limitBytes());
    }

    @Test
    void largeNonJavaFilesAreNotLimited() throws IOException {
        write("image.png", "x".repeat(50));             // bigger than the limit, but not Java
        SourceInventory tenByteLimit = new SourceInventory(SourceInventory.DEFAULT_EXCLUDED_DIRECTORIES, 10);

        InventoryResult result = tenByteLimit.scan(root);

        assertEquals(List.of(new SourceFile("image.png", 50, null)), result.files());
    }

    @Test
    void rejectsMissingRoot() {
        assertThrows(NoSuchFileException.class, () -> inventory.scan(root.resolve("does-not-exist")));
    }

    @Test
    void rejectsRootThatIsAFile() throws IOException {
        Path file = write("plain.txt", "x");

        assertThrows(NotDirectoryException.class, () -> inventory.scan(file));
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
