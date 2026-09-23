package fixtures.declarations;

// Java writes hidden ("implicit") members for these types. CodePulse counts only what is
// actually written in the file, so every method/constructor count here is 0.

record Point(int x, int y) {}   // implicit: canonical constructor, x(), y(), equals, hashCode, toString

enum Color { RED, GREEN }       // implicit: values(), valueOf(String), private constructor

class Empty {}                  // implicit: default constructor

// EXPECTED: class 1, interface 0, enum 1, record 1, annotation 0, anonymous 0,
//           method 0, constructor 0, executable 0, lambda 0
