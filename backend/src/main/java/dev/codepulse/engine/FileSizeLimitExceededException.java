package dev.codepulse.engine;

import java.io.IOException;

/**
 * A file is bigger than the configured limit, so the run must stop (Blueprint 11.3:
 * "Single Java file 256 KiB — reject run for configured limit").
 *
 * <p>It extends IOException (a checked exception) because it describes the input data, not a bug
 * in our code, and the caller (later: the analysis worker) must decide how to report it.
 */
public class FileSizeLimitExceededException extends IOException {

    private final String relativePath;
    private final long limitBytes;

    public FileSizeLimitExceededException(String relativePath, long limitBytes) {
        super("file exceeds " + limitBytes + " byte limit: " + relativePath);
        this.relativePath = relativePath;
        this.limitBytes = limitBytes;
    }

    public String relativePath() {
        return relativePath;
    }

    public long limitBytes() {
        return limitBytes;
    }
}
