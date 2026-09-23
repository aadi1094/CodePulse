package fixtures.complexity;

import java.util.List;

// Every method states its expected CodePulse complexity and why (Blueprint 9.4).
abstract class Complexity {

    // expected 5: the Blueprint example. 1 + if + && + for + if
    int fee(boolean premium, int units) {
        if (premium && units > 10) return 0;
        for (int i = 0; i < units; i++) {
            if (i % 2 == 0) audit(i);
        }
        return 1;
    }

    // expected 1: straight-line code
    void audit(int i) {
        System.out.println(i);
    }

    // expected null: abstract, no body
    abstract int unknown();

    // expected 4: 1 + if + if (else-if) + if (else-if). "else" adds 0
    String grade(int score) {
        if (score > 90) {
            return "A";
        } else if (score > 75) {
            return "B";
        } else if (score > 50) {
            return "C";
        } else {
            return "F";
        }
    }

    // expected 3: 1 + ternary + nested ternary
    String sign(int x) {
        return x > 0 ? "positive" : x < 0 ? "negative" : "zero";
    }

    // expected 4: 1 + && + || + ||   (counted anywhere, not only inside an if)
    boolean valid(boolean a, boolean b, boolean c, boolean d) {
        return a && b || c || d;
    }

    // expected 1: bitwise & and | are not short-circuit operators
    int bits(int a, int b) {
        return (a & b) | (a ^ b);
    }

    // expected 3: 1 + case "a","b" (ONE grouped entry) + case "c". default adds 0
    int grouped(String s) {
        return switch (s) {
            case "a", "b" -> 1;
            case "c" -> 2;
            default -> 0;
        };
    }

    // expected 3: 1 + case 1 + case 2 (old style: two separate entries). default adds 0
    int oldSwitch(int n) {
        int r;
        switch (n) {
            case 1:
            case 2:
                r = 1;
                break;
            default:
                r = 2;
        }
        return r;
    }

    // expected 5: 1 + case Integer + its "when" guard + && in the guard + case String
    String describe(Object o) {
        return switch (o) {
            case Integer i when i > 0 && i < 10 -> "small";
            case String s -> "text";
            default -> "other";
        };
    }

    // expected 4: 1 + while + do-while + for-each
    int loops(List<Integer> xs) {
        int n = 0;
        while (n < 3) n++;
        do {
            n--;
        } while (n > 0);
        for (int x : xs) n += x;
        return n;
    }

    // expected 3: 1 + catch (multi-catch A | B is ONE catch) + catch. try and finally add 0
    int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        } catch (RuntimeException e) {
            return -2;
        } finally {
            audit(0);
        }
    }

    // expected 2: 1 + if. The if and && inside the lambda body are skipped
    Runnable lambda(boolean flag) {
        if (flag) return () -> {};
        return () -> {
            if (flag && flag) audit(1);
        };
    }

    // expected 1: the anonymous class's own if belongs to its run(), not here
    Runnable anonymous() {
        return new Runnable() {
            // expected 2: 1 + if
            public void run() {
                if (Math.random() > 0.5) audit(2);
            }
        };
    }

    // expected 2: 1 + && in the constructor ARGUMENT (arguments count; the anonymous body does not)
    Thread anonymousWithArgument(boolean a, boolean b) {
        return new Thread(String.valueOf(a && b)) {
            // expected 1
            @Override
            public void run() {
            }
        };
    }

    // expected 1: the local class's method is its own unit
    void local() {
        class Helper {
            // expected 2: 1 + for
            void work() {
                for (int i = 0; i < 3; i++) audit(i);
            }
        }
        new Helper().work();
    }

    // expected 2: 1 + if (constructors are measured too)
    Complexity(int start) {
        if (start < 0) throw new IllegalArgumentException();
    }
}
