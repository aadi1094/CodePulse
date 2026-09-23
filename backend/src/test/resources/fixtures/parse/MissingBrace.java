package fixtures.parse;

// Fixture: the class body is never closed. JavaParser recovers a partial tree here;
// CodePulse must still report PARSE_FAILED.
class MissingBrace {
    void check() {
        int passwordValue = 1;
    }
