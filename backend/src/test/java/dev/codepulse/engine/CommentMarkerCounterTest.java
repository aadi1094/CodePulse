package dev.codepulse.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Each expected pair (todo, fixme) was counted by hand from the source string. */
class CommentMarkerCounterTest {

    private final JavaSourceParser parser = new JavaSourceParser();

    @Test
    void countsMarkersInLineBlockAndJavadocComments() {
        String source = """
            // TODO fix the rounding
            /* FIXME this breaks on Sundays */
            /**
             * todo: explain the return value
             */
            class A {}
            """;
        assertEquals(new CommentMarkers(2, 1), markers(source));
    }

    @Test
    void countsOccurrencesNotLines() {
        assertEquals(new CommentMarkers(3, 0), markers("// TODO TODO todo\nclass A {}\n"));
    }

    @Test
    void wholeWordsOnlyAnyCase() {
        // counted: "(todo)", "@Todo", "TODO.", "FiXmE:"  -> todo 3, fixme 1
        assertEquals(new CommentMarkers(3, 1), markers("// (todo) @Todo TODO. FiXmE:\nclass A {}\n"));
    }

    @Test
    void partOfALongerWordDoesNotCount() {
        // "TODOs", "TODO_LATER", "xTODO", "FIXMEs", "FIX ME" -> none are whole-word markers
        assertEquals(new CommentMarkers(0, 0),
            markers("// TODOs TODO_LATER xTODO FIXMEs FIX ME\nclass A {}\n"));
    }

    @Test
    void markersOutsideCommentsAreIgnored() {
        // A string "TODO" and a variable named FIXME are code, not comments.
        assertEquals(new CommentMarkers(0, 0),
            markers("class A { String s = \"TODO\"; int FIXME = 1; }\n"));
    }

    @Test
    void emptyFileHasNoMarkers() {
        assertEquals(new CommentMarkers(0, 0), markers(""));
    }

    private CommentMarkers markers(String source) {
        return CommentMarkerCounter.count(parser.parse(source).syntaxTree().orElseThrow());
    }
}
