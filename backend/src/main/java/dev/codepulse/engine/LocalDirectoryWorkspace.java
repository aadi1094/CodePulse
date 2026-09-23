package dev.codepulse.engine;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A workspace that is simply a trusted folder already on this computer: a test fixture or a folder
 * a developer points the CLI harness at. Never built from a path received over HTTP
 * (Blueprint 11.3: "Never accept a server filesystem path via REST").
 *
 * <p>A record can implement an interface. The record's generated accessor {@code root()} is exactly
 * the method SourceWorkspace promises, so no extra code is needed.
 */
public record LocalDirectoryWorkspace(Path root) implements SourceWorkspace {

    public LocalDirectoryWorkspace {
        Objects.requireNonNull(root, "root must not be null");
    }
}
