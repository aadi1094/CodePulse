package dev.codepulse.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lists the regular files under a trusted directory, skipping excluded directories.
 *
 * <p>Inventory only answers "which files are here and how big are they". It does not read or
 * parse file contents; that is later work. Rules (Blueprint 9.2):
 * <ul>
 *   <li>Paths are relative to the root and use "/" so results are the same on every OS.</li>
 *   <li>Output is sorted, because the filesystem returns files in no guaranteed order.</li>
 *   <li>Symbolic links are never followed and never listed.</li>
 *   <li>Files inside an excluded directory are counted, not silently dropped.</li>
 *   <li>{@code .java} files get a physical line count and a {@link SourceRole}; a Java file above
 *       the size limit stops the scan.</li>
 * </ul>
 */
public final class SourceInventory {

    /**
     * Directory names skipped by default, matched exactly against any directory segment of a path.
     * From Blueprint 9.2; "generated" and "generated-sources" stand in for "generated-source directories".
     */
    public static final Set<String> DEFAULT_EXCLUDED_DIRECTORIES =
        Set.of(".git", "node_modules", "vendor", "target", "build", "generated", "generated-sources");

    /** Largest .java file we will read: 256 KiB (Blueprint 11.3). */
    public static final long DEFAULT_MAX_JAVA_FILE_BYTES = 256 * 1024;

    private final Set<String> excludedDirectoryNames;
    private final long maxJavaFileBytes;
    private final PhysicalLineCounter lineCounter = new PhysicalLineCounter();

    public SourceInventory() {
        this(DEFAULT_EXCLUDED_DIRECTORIES, DEFAULT_MAX_JAVA_FILE_BYTES);
    }

    public SourceInventory(Set<String> excludedDirectoryNames, long maxJavaFileBytes) {
        if (maxJavaFileBytes <= 0) {
            throw new IllegalArgumentException("maxJavaFileBytes must be > 0");
        }
        this.excludedDirectoryNames = Set.copyOf(excludedDirectoryNames);
        this.maxJavaFileBytes = maxJavaFileBytes;
    }

    /**
     * @param workspace where the files are; today a trusted local folder, later a downloaded archive
     * @return included files sorted by relative path, plus the count of excluded files
     * @throws NoSuchFileException   if root does not exist
     * @throws NotDirectoryException if root is a file or a symbolic link
     * @throws FileSizeLimitExceededException if a .java file is larger than the limit
     * @throws IOException           if the filesystem cannot be read
     */
    public InventoryResult scan(SourceWorkspace workspace) throws IOException {
        Path base = workspace.root().toAbsolutePath().normalize();
        if (!Files.exists(base, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(base.toString());
        }
        if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) {
            throw new NotDirectoryException(base.toString());
        }

        List<SourceFile> files = new ArrayList<>();
        int excluded = 0;

        // Files.walk opens directory handles in the operating system. try-with-resources calls
        // paths.close() when the block ends, whether it ends normally or by an exception, so the
        // handles are always released. Without it, each scan would leak OS resources.
        try (Stream<Path> paths = Files.walk(base)) {
            // A plain loop instead of paths.forEach(...): Files.size throws the checked
            // IOException, and a lambda passed to forEach is not allowed to throw it.
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                // NOFOLLOW_LINKS: a symlink is not a regular file, so it is skipped here.
                // Files.walk itself does not follow links unless asked to.
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                Path relative = base.relativize(path);
                if (isInsideExcludedDirectory(relative)) {
                    excluded++;
                    continue;
                }
                files.add(measure(path, toPosixPath(relative)));
            }
        } catch (UncheckedIOException e) {
            // The stream's iterator cannot throw checked exceptions, so it wraps them.
            // Unwrap so callers see the real IOException (e.g. AccessDeniedException).
            throw e.getCause();
        }

        files.sort(Comparator.comparing(SourceFile::relativePath));
        return new InventoryResult(files, excluded);
    }

    private SourceFile measure(Path path, String relativePath) throws IOException {
        long size = Files.size(path);
        if (!relativePath.endsWith(".java")) {
            return SourceFile.other(relativePath, size);   // listed, not measured
        }
        // Cheap early check using the size the filesystem reports ...
        if (size > maxJavaFileBytes) {
            throw new FileSizeLimitExceededException(relativePath, maxJavaFileBytes);
        }
        // ... and the counter enforces the limit again on the bytes it actually reads.
        int lines = lineCounter.count(path, relativePath, maxJavaFileBytes);
        return new SourceFile(relativePath, size, lines, SourceRole.classify(relativePath));
    }

    /** True if any directory segment (not the file name itself) is an excluded name. */
    private boolean isInsideExcludedDirectory(Path relative) {
        int directorySegments = relative.getNameCount() - 1;
        for (int i = 0; i < directorySegments; i++) {
            if (excludedDirectoryNames.contains(relative.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }

    /** Joins path segments with "/" so Windows and macOS produce the same text. */
    private static String toPosixPath(Path relative) {
        StringBuilder out = new StringBuilder();
        for (Path segment : relative) {
            if (!out.isEmpty()) {
                out.append('/');
            }
            out.append(segment);
        }
        return out.toString();
    }
}
