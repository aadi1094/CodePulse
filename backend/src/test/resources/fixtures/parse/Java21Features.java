package fixtures.parse;

// Fixture: valid Java 21 without previews. Record patterns, sealed types, text blocks,
// pattern matching for switch with a guard. All of these are FINAL features in Java 21.
sealed interface Shape permits Circle, Square {}

record Circle(double radius) implements Shape {}

record Square(double side) implements Shape {}

class Area {
    String banner = """
        area report
        """;

    double area(Shape shape) {
        return switch (shape) {
            case Circle c when c.radius() > 0 -> Math.PI * c.radius() * c.radius();
            case Circle c -> 0;
            case Square(double side) -> side * side;
        };
    }
}
