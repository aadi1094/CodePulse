package dev.codepulse.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Counts physical lines as defined by {@code java-metrics-v1} {@code physicalLoc} (Blueprint 9.3):
 * <ul>
 *   <li>an empty file has 0 lines;</li>
 *   <li>a trailing line break does not create an extra empty line;</li>
 *   <li>{@code \r\n} counts as ONE line break. A lone {@code \n} or lone {@code \r} also counts as
 *       one, matching Java's own definition of a line terminator (JLS 3.4).</li>
 * </ul>
 *
 * <p>It counts raw bytes, not decoded characters. That is safe for UTF-8: the bytes for '\n' and
 * '\r' never appear inside a multi-byte character. Checking that a file really is valid UTF-8 is
 * parsing work (Phase 2), not inventory work.
 *
 * <p>It never reads more than {@code maxBytes + 1} bytes, even if the file grows while being read.
 */
final class PhysicalLineCounter {

    private static final int BUFFER_SIZE = 8192;

    /**
     * @throws FileSizeLimitExceededException if the file holds more than maxBytes bytes
     */
    int count(Path file, String relativePath, long maxBytes) throws IOException {
        int lines = 0;
        long totalBytes = 0;
        boolean previousWasCarriageReturn = false;
        int lastByte = -1;

        // Same "always close the door" rule as Files.walk: the stream holds an OS file handle.
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                totalBytes += read;
                if (totalBytes > maxBytes) {
                    // Stop immediately: never read an unbounded amount of untrusted data.
                    throw new FileSizeLimitExceededException(relativePath, maxBytes);
                }
                for (int i = 0; i < read; i++) {
                    byte b = buffer[i];
                    if (b == '\n') {
                        if (!previousWasCarriageReturn) {
                            lines++;          // a plain \n
                        }                     // else: the \n of \r\n, already counted at \r
                        previousWasCarriageReturn = false;
                    } else if (b == '\r') {
                        lines++;
                        previousWasCarriageReturn = true;
                    } else {
                        previousWasCarriageReturn = false;
                    }
                    lastByte = b;
                }
                // previousWasCarriageReturn survives to the next buffer, so a \r\n split across
                // two reads is still one line break.
            }
        }

        boolean endsWithLineBreak = lastByte == '\n' || lastByte == '\r';
        if (totalBytes > 0 && !endsWithLineBreak) {
            lines++;                          // the last line had no line break after it
        }
        return lines;
    }
}
