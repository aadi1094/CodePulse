package fixtures.parse;

// Fixture: "_" as an unnamed variable was a PREVIEW feature in Java 21 (JEP 443).
class UnnamedVariable {
    void run() {
        int _ = 42;
    }
}
