package dev.codepulse.engine;

/** What kind of executable declaration a method record describes ({@code declaration_kind} in the schema). */
public enum DeclarationKind {
    METHOD,
    CONSTRUCTOR,
    /** A record's compact constructor: {@code Item { ... }} with no parameter list written. */
    COMPACT_CONSTRUCTOR
}
