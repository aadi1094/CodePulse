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
 * </ul>
 */
public final class SourceInventory {

    /**
     * Directory names skipped by default, matched exactly against any directory segment of a path.
     * From Blueprint 9.2; "generated" and "generated-sources" stand in for "generated-source directories".
     */
    public static final Set<String> DEFAULT_EXCLUDED_DIRECTORIES =
        Set.of(".git", "node_modules", "vendor", "target", "build", "generated", "generated-sources");

    private final Set<String> excludedDirectoryNames;

    public SourceInventory() {
        this(DEFAULT_EXCLUDED_DIRECTORIES);
    }

    public SourceInventory(Set<String> excludedDirectoryNames) {
        this.excludedDirectoryNames = Set.copyOf(excludedDirectoryNames);
    }

    /**
     * @param root a trusted local directory (a test fixture or developer-chosen folder)
     * @return included files sorted by relative path, plus the count of excluded files
     * @throws NoSuchFileException   if root does not exist
     * @throws NotDirectoryException if root is a file or a symbolic link
     * @throws IOException           if the filesystem cannot be read
     */
    public InventoryResult scan(Path root) throws IOException {
        Path base = root.toAbsolutePath().normalize();
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
                files.add(new SourceFile(toPosixPath(relative), Files.size(path)));
            }
        } catch (UncheckedIOException e) {
            // The stream's iterator cannot throw checked exceptions, so it wraps them.
            // Unwrap so callers see the real IOException (e.g. AccessDeniedException).
            throw e.getCause();
        }

        files.sort(Comparator.comparing(SourceFile::relativePath));
        return new InventoryResult(files, excluded);
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
