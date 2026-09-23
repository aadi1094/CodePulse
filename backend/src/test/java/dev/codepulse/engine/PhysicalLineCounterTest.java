package dev.codepulse.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every expected number here is counted by hand from the text, not computed by a formula.
 * Read each string and count the lines yourself before trusting the test.
 */
class PhysicalLineCounterTest {

    private static final long NO_REAL_LIMIT = 1_000_000;

    @TempDir
    Path dir;

    private final PhysicalLineCounter counter = new PhysicalLineCounter();

    @Test
    void emptyFileHasZeroLines() throws IOException {
        assertEquals(0, lines(""));
    }

    @Test
    void oneLineWithoutTrailingNewline() throws IOException {
        assertEquals(1, lines("class A {}"));
    }

    @Test
    void trailingNewlineDoesNotAddALine() throws IOException {
        assertEquals(1, lines("class A {}\n"));
    }

    @Test
    void blankLinesInTheMiddleAreStillLines() throws IOException {
        // line 1: "a", line 2: empty, line 3: "b"
        assertEquals(3, lines("a\n\nb\n"));
    }

    @Test
    void aFileWithOnlyANewlineHasOneEmptyLine() throws IOException {
        assertEquals(1, lines("\n"));
    }

    @Test
    void windowsLineEndingsCountOncePerLine() throws IOException {
        assertEquals(3, lines("a\r\nb\r\nc\r\n"));
        assertEquals(lines("a\nb\nc\n"), lines("a\r\nb\r\nc\r\n"));
    }

    @Test
    void loneCarriageReturnIsALineBreak() throws IOException {
        assertEquals(2, lines("a\rb"));
    }

    @Test
    void mixedLineEndingsInOneFile() throws IOException {
        // "a" ends with \n, "b" ends with \r\n, "c" ends with \r, "d" has no line break
        assertEquals(4, lines("a\nb\r\nc\rd"));
    }

    @Test
    void crlfSplitAcrossTheReadBufferIsStillOneLineBreak() throws IOException {
        // 8191 letters put the \r as the last byte of the first 8192-byte read
        // and the \n as the first byte of the second read.
        String text = "a".repeat(8191) + "\r\n" + "b";
        assertEquals(2, lines(text));
    }

    @Test
    void utf8MultiByteCharactersDoNotConfuseTheCount() throws IOException {
        String text = "// héllo\nclass 日本 {}\n";
        Path file = write(text);

        assertEquals(2, counter.count(file, "U.java", NO_REAL_LIMIT));
        // Characters: "// héllo" 8 + "\n" 1 + "class 日本 {}" 11 + "\n" 1 = 21.
        // Bytes: é is 2 bytes and 日, 本 are 3 bytes each, so 21 + 1 + 2 + 2 = 26.
        assertEquals(21, text.length());
        assertEquals(26, Files.size(file));
    }

    @Test
    void stopsReadingOnceTheLimitIsPassed() throws IOException {
        Path file = write("0123456789X"); // 11 bytes

        assertEquals(1, counter.count(file, "Ok.java", 11));
        assertThrows(FileSizeLimitExceededException.class, () -> counter.count(file, "Big.java", 10));
    }

    private int lines(String text) throws IOException {
        return counter.count(write(text), "T.java", NO_REAL_LIMIT);
    }

    private Path write(String text) throws IOException {
        Path file = Files.createTempFile(dir, "fixture", ".java");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }
}
