package dev.codepulse.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Expected values are counted by hand, line by line, before running the code. */
class LineMetricsCalculatorTest {

    private final JavaSourceParser parser = new JavaSourceParser();

    /**
     * fixtures/lines/Mixed.java, line by line:
     * <pre>
     *  1 package fixtures.lines;                 code
     *  2                                         blank
     *  3 /**                                     comment
     *  4  * Javadoc is a comment.                comment
     *  5  *&#47;                                 comment
     *  6 class Mixed {                           code
     *  7     String url = "http://..."; // ...   code + comment  ("//" in the string is NOT a comment)
     *  8     /* block comment                    comment
     *  9                                         comment + blank (empty line inside the comment)
     * 10        still the comment *&#47; int...  comment + code
     * 11                                         blank
     * 12     String text = """                   code (text block starts)
     * 13         first                           code (inside text block)
     * 14                                         code + blank (empty line inside text block)
     * 15         """;                            code
     * 16 }                                       code
     * </pre>
     * code: 1,6,7,10,12,13,14,15,16 = 9. comment: 3,4,5,7,8,9,10 = 7. blank: 2,9,11,14 = 4.
     */
    @Test
    void mixedFixtureMatchesTheLineByLineHandCount() throws IOException {
        assertEquals(new LineMetrics(9, 7, 4), metrics(fixture("Mixed.java")));
    }

    @Test
    void emptyFileHasNoLinesOfAnyKind() {
        assertEquals(new LineMetrics(0, 0, 0), metrics(""));
    }

    @Test
    void aFileWithOnlyACommentHasNoCode() {
        assertEquals(new LineMetrics(0, 1, 0), metrics("// nothing to see here\n"));
    }

    @Test
    void doubleSlashInsideAStringIsNotAComment() {
        assertEquals(new LineMetrics(1, 0, 0), metrics("class A { String s = \"// not a comment\"; }\n"));
    }

    @Test
    void spacesAndTabsOnlyLineIsBlank() {
        // line 1 code, line 2 is "   \t" (blank), line 3 code
        assertEquals(new LineMetrics(2, 0, 1), metrics("class A {\n   \t\n}\n"));
    }

    @Test
    void windowsAndOldMacLineEndingsGiveTheSameCounts() {
        LineMetrics unix = metrics("class A {\n\n}\n");
        assertEquals(new LineMetrics(2, 0, 1), unix);
        assertEquals(unix, metrics("class A {\r\n\r\n}\r\n"));
        assertEquals(unix, metrics("class A {\r\r}\r"));
    }

    @Test
    void lastLineWithoutLineBreakIsStillCounted() {
        // line 1 code, line 2 "  " blank with no line break after it
        assertEquals(new LineMetrics(1, 0, 1), metrics("class A {}\n  "));
    }

    private LineMetrics metrics(String source) {
        return LineMetricsCalculator.calculate(parser.parse(source).syntaxTree().orElseThrow(), source);
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/lines/" + name)) {
            assertNotNull(in, "missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
