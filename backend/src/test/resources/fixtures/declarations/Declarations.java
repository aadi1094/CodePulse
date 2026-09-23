package fixtures.declarations;

import java.util.function.Supplier;

// Hand-counted fixture for java-metrics-v1 declaration counts (Blueprint 9.3).
// Running tally on the right. Expected totals are at the bottom of the file.

@interface Audit {                              // annotation 1
    String value();                             // annotation member: NOT a method
}

interface Greeter {                             // interface 1
    String greet(String name);                  // method 1  (abstract: no body)

    default String greet() {                    // method 2  executable 1
        return greet("world");
    }

    static Greeter polite() {                   // method 3  executable 2
        return name -> "Hello " + name;         // lambda 1
    }
}

class Shop {                                    // class 1
    private final int size;

    Shop() {                                    // constructor 1  executable 3
        this(1);
    }

    Shop(int size) {                            // constructor 2  executable 4
        this.size = size;
    }

    int price(int units) {                      // method 4  executable 5
        return units * 2;
    }

    int price(int units, int discount) {        // method 5  executable 6  (overload)
        return price(units) - discount;
    }

    Runnable task() {                           // method 6  executable 7
        class Local {                           // class 2  (local class)
            void run() {                        // method 7  executable 8
            }
        }
        return new Runnable() {                 // anonymous 1  (NOT counted as a class)
            public void run() {                 // method 8  executable 9
                new Local().run();
            }
        };
    }

    enum Size {                                 // enum 1
        SMALL {
            int weight() {                      // method 9  executable 10
                return 1;
            }
        };

        abstract int weight();                  // method 10  (abstract: no body)
    }

    record Item(String name, int qty) {         // record 1
        Item {                                  // constructor 3 (compact)  executable 11
            if (qty < 0) {
                throw new IllegalArgumentException("qty");
            }
        }
    }

    Supplier<Item> maker = () -> new Item("x", 1);   // lambda 2
}

// EXPECTED: class 2, interface 1, enum 1, record 1, annotation 1, anonymous 1,
//           method 10, constructor 3, executable 11, lambda 2
