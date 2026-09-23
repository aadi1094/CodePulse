package dev.codepulse.engine;

/**
 * What kind of source a Java file is (Blueprint 9.2; named {@code source_scope} in the schema
 * and {@code sourceScope} in the API contract).
 *
 * <p>An enum is a fixed list of named values. The compiler rejects anything else, so a typo like
 * "MIAN" cannot sneak in the way it could with a plain String.
 */
public enum SourceScope {
    /** Production code: a path under {@code src/main/}. */
    MAIN,
    /** Test code: a path under {@code src/test/}. Shown separately so tests do not dilute results. */
    TEST,
    /** Any other Java file, e.g. {@code tools/Gen.java} or {@code examples/Demo.java}. */
    OTHER_SOURCE;

    /**
     * Finds the first "src" directory followed by "main" or "test", anywhere in the path, so
     * multi-module projects like {@code billing/src/main/java/A.java} are MAIN too.
     * Only directory segments are checked, never the file name, and names must match exactly.
     */
    static SourceScope classify(String relativePath) {
        String[] segments = relativePath.split("/");
        int directorySegments = segments.length - 1;
        for (int i = 0; i + 1 < directorySegments; i++) {
            if (segments[i].equals("src")) {
                if (segments[i + 1].equals("main")) {
                    return MAIN;
                }
                if (segments[i + 1].equals("test")) {
                    return TEST;
                }
            }
        }
        return OTHER_SOURCE;
    }
}
