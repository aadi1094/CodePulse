package dev.codepulse.engine;

/**
 * One explicit method or constructor in a parsed file (Blueprint 9.3 and 9.5; {@code method_metric}
 * in the schema, {@code MethodMetric} in the API). Complexity is added in a later slice.
 *
 * <p>Identity inside one file is (ownerLabel, signature, beginLine). Overloads such as
 * {@code price(int)} and {@code price(int, int)} have different signatures, so they never overwrite
 * each other. This is not a stable identity across refactors, only within one snapshot.
 *
 * @param ownerLabel      e.g. {@code Shop}, {@code Shop.Size}, {@code Shop#Local}, {@code Shop#anonymous@48}.
 *                        "." joins real member-type names; "#" marks local, anonymous, or enum-constant
 *                        bodies, which have no importable name.
 * @param signature       name plus declared parameter types in normalized form (no spaces or comments
 *                        inside a type), e.g. {@code price(int, int)}, {@code f(Map<String,Integer>)}
 * @param declarationKind METHOD, CONSTRUCTOR, or COMPACT_CONSTRUCTOR
 * @param hasBody         false for abstract and plain interface methods
 * @param beginLine       first line, including annotations (a Javadoc comment is not part of it)
 * @param endLine         last line
 * @param ncloc           distinct lines in the declaration's range touched by code tokens,
 *                        including the signature and braces
 */
public record MethodMeasurement(
    String ownerLabel,
    String signature,
    DeclarationKind declarationKind,
    boolean hasBody,
    int beginLine,
    int endLine,
    int ncloc) {

    public MethodMeasurement {
        if (ownerLabel == null || ownerLabel.isBlank() || signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("ownerLabel and signature must not be blank");
        }
        if (declarationKind == null) {
            throw new IllegalArgumentException("declarationKind must not be null");
        }
        if (declarationKind != DeclarationKind.METHOD && !hasBody) {
            throw new IllegalArgumentException("constructors always have a body");
        }
        if (beginLine < 1 || endLine < beginLine) {
            throw new IllegalArgumentException("invalid line range " + beginLine + "-" + endLine);
        }
        if (ncloc < 0) {
            throw new IllegalArgumentException("ncloc must be >= 0");
        }
    }
}
